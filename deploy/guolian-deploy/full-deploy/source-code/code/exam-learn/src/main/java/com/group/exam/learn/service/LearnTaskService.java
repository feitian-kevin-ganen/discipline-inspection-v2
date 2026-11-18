package com.group.exam.learn.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.learn.dto.LearnTaskCreateRequest;
import com.group.exam.learn.entity.LearnMaterial;
import com.group.exam.learn.entity.LearnRecord;
import com.group.exam.learn.entity.LearnTask;
import com.group.exam.learn.entity.LearnTaskUser;
import com.group.exam.learn.mapper.LearnTaskMapper;
import com.group.exam.learn.mapper.LearnTaskUserMapper;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.OrgUserService;
import com.group.exam.system.service.SysLogService;
import com.group.exam.system.service.SysNoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习任务服务（LEARN-06/07/08 + 完成后回刷任务人员状态）
 */
@Service
public class LearnTaskService extends ServiceImpl<LearnTaskMapper, LearnTask> {

    private final LearnTaskUserMapper taskUserMapper;
    private final LearnMaterialService materialService;
    private final LearnRecordService recordService;
    private final OrgUnitService orgUnitService;
    private final OrgUserService orgUserService;
    private final SysNoticeService sysNoticeService;
    private final SysLogService sysLogService;

    public LearnTaskService(LearnTaskUserMapper taskUserMapper, LearnMaterialService materialService,
                            LearnRecordService recordService, OrgUnitService orgUnitService,
                            OrgUserService orgUserService, SysNoticeService sysNoticeService,
                            SysLogService sysLogService) {
        this.taskUserMapper = taskUserMapper;
        this.materialService = materialService;
        this.recordService = recordService;
        this.orgUnitService = orgUnitService;
        this.orgUserService = orgUserService;
        this.sysNoticeService = sysNoticeService;
        this.sysLogService = sysLogService;
    }

    // ---------------------------------------------------------------- LEARN-08 下达学习任务

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(LearnTaskCreateRequest req) {
        LoginUser current = SecurityUtils.getLoginUser();
        // 资料须全部已发布
        List<LearnMaterial> materials = new ArrayList<>();
        for (Long materialId : new LinkedHashSet<>(req.getMaterialIds())) {
            materials.add(materialService.requirePublished(materialId));
        }
        // 目标单位展开为 orgPath（含下级），逐单位做数据权限校验
        List<String> orgPaths = new ArrayList<>();
        if (req.getTargetOrgIds() != null) {
            for (Long orgId : new LinkedHashSet<>(req.getTargetOrgIds())) {
                OrgUnit unit = orgUnitService.getUnit(orgId);
                if (unit == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "目标单位不存在: " + orgId);
                }
                SecurityUtils.checkOrgScope(unit.getPath());
                orgPaths.add(unit.getPath());
            }
        }
        if (orgPaths.isEmpty() && (req.getUserIds() == null || req.getUserIds().isEmpty())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目标范围不能为空");
        }

        // 人员快照展开：范围内在册人员 + 指定人员合并去重
        Map<Long, OrgUser> assignees = new LinkedHashMap<>();
        for (OrgUser u : orgUserService.listActiveByOrgPaths(orgPaths, true)) {
            assignees.put(u.getId(), u);
        }
        if (req.getUserIds() != null && !req.getUserIds().isEmpty()) {
            for (OrgUser u : orgUserService.listByIds(req.getUserIds())) {
                if (u.getAccountStatus() == null || u.getAccountStatus() != 0) {
                    SecurityUtils.checkOrgScope(u.getOrgPath());
                    assignees.putIfAbsent(u.getId(), u);
                }
            }
        }

        Map<String, Object> targetScope = new HashMap<>();
        targetScope.put("orgPaths", orgPaths);
        targetScope.put("withChildren", true);
        targetScope.put("userIds", req.getUserIds() == null ? List.of() : req.getUserIds());

        LearnTask task = new LearnTask();
        task.setTaskName(req.getTitle());
        task.setMaterialIds(materials.stream().map(LearnMaterial::getId).collect(Collectors.toList()));
        task.setRequiredDurationSec(req.getRequiredSec());
        task.setDeadline(req.getDeadline());
        task.setTargetScope(targetScope);
        task.setPublishOrgId(current.getOrgId());
        task.setPublishOrgPath(current.getOrgPath());
        task.setStatus(1);
        task.setRemark(req.getRemark());
        task.setCreateBy(current.getUserId());
        save(task);

        Map<Long, String> orgNames = orgUnitService.mapUnitNames(
                assignees.values().stream().map(OrgUser::getOrgId).collect(Collectors.toSet()));
        List<LearnTaskUser> rows = new ArrayList<>(assignees.size());
        for (OrgUser u : assignees.values()) {
            LearnTaskUser row = new LearnTaskUser();
            row.setTaskId(task.getId());
            row.setUserId(u.getId());
            row.setUserName(u.getUserName());
            row.setOrgId(u.getOrgId());
            row.setOrgPath(u.getOrgPath());
            row.setOrgName(orgNames.getOrDefault(u.getOrgId(), ""));
            row.setCompleteStatus(0);
            row.setCreateBy(current.getUserId());
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            rows.forEach(taskUserMapper::insert);
        }
        // 站内通知（noticeType=3 学习任务提醒）
        sysNoticeService.sendBatch(3, "学习任务提醒：" + task.getTaskName(),
                "请及时完成学习任务[" + task.getTaskName() + "]，期限 " + task.getDeadline(),
                "task", task.getId(), assignees.keySet());
        sysLogService.log(1, "LEARN", "下达学习任务",
                "任务[" + task.getTaskName() + "(id=" + task.getId() + ")]，覆盖 " + rows.size() + " 人", 1);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("assigneeCount", rows.size());
        return data;
    }

    // ---------------------------------------------------------------- LEARN-07 学习任务分页查询（管理端）

    public PageResult<Map<String, Object>> pageAdmin(String keyword, String status, long pageNum, long pageSize) {
        QueryWrapper<LearnTask> qw = new QueryWrapper<>();
        String prefix = SecurityUtils.dataScopePathPrefix();
        qw.likeRight(prefix != null, "publish_org_path", prefix);
        qw.like(StringUtils.hasText(keyword), "task_name", keyword);
        LocalDateTime now = LocalDateTime.now();
        if (StringUtils.hasText(status)) {
            switch (status) {
                case "ONGOING" -> qw.eq("status", 1).ge("deadline", now);
                case "EXPIRED" -> qw.eq("status", 1).lt("deadline", now);
                case "ENDED" -> qw.eq("status", 2);
                case "REVOKED" -> qw.eq("status", 0);
                default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法 status: " + status);
            }
        }
        qw.orderByDesc("create_time");
        Page<LearnTask> page = page(new Page<>(pageNum, pageSize), qw);
        List<LearnTask> tasks = page.getRecords();

        // 任务完成人数统计（本页任务批量聚合）
        Map<Long, long[]> stats = new HashMap<>(); // taskId → [assigneeCount, doneCount]
        if (!tasks.isEmpty()) {
            List<Map<String, Object>> rows = taskUserMapper.selectMaps(new QueryWrapper<LearnTaskUser>()
                    .select("task_id", "COUNT(*) AS total",
                            "COALESCE(SUM(complete_status),0) AS done")
                    .in("task_id", tasks.stream().map(LearnTask::getId).collect(Collectors.toList()))
                    .groupBy("task_id"));
            for (Map<String, Object> row : rows) {
                stats.put(((Number) row.get("task_id")).longValue(), new long[]{
                        ((Number) row.get("total")).longValue(), ((Number) row.get("done")).longValue()});
            }
        }
        // 平均进度（与用户端口径一致）：单人员进度 = 任务各资料 progress 均值（完成=100，无记录=0），
        // 任务平均进度 = 各人员进度均值
        Map<Long, BigDecimal> avgProgressMap = new HashMap<>();
        if (!tasks.isEmpty()) {
            List<Long> pageTaskIds = tasks.stream().map(LearnTask::getId).collect(Collectors.toList());
            List<LearnTaskUser> assigneeRows = taskUserMapper.selectList(new QueryWrapper<LearnTaskUser>()
                    .select("task_id", "user_id").in("task_id", pageTaskIds));
            Set<Long> allUserIds = assigneeRows.stream().map(LearnTaskUser::getUserId).collect(Collectors.toSet());
            Set<Long> allMatIds = new LinkedHashSet<>();
            for (LearnTask t : tasks) {
                if (t.getMaterialIds() != null) {
                    allMatIds.addAll(t.getMaterialIds());
                }
            }
            Map<String, LearnRecord> recMap = new HashMap<>();
            if (!allUserIds.isEmpty() && !allMatIds.isEmpty()) {
                for (LearnRecord r : recordService.list(new QueryWrapper<LearnRecord>()
                        .select("user_id", "material_id", "progress")
                        .in("user_id", allUserIds).in("material_id", allMatIds))) {
                    recMap.put(r.getUserId() + ":" + r.getMaterialId(), r);
                }
            }
            Map<Long, List<Long>> taskUsers = assigneeRows.stream().collect(Collectors.groupingBy(
                    LearnTaskUser::getTaskId, Collectors.mapping(LearnTaskUser::getUserId, Collectors.toList())));
            for (LearnTask t : tasks) {
                List<Long> uids = taskUsers.getOrDefault(t.getId(), List.of());
                List<Long> mids = t.getMaterialIds() == null ? List.of() : t.getMaterialIds();
                if (uids.isEmpty() || mids.isEmpty()) {
                    avgProgressMap.put(t.getId(), BigDecimal.ZERO);
                    continue;
                }
                BigDecimal sum = BigDecimal.ZERO;
                for (Long uid : uids) {
                    BigDecimal userSum = BigDecimal.ZERO;
                    for (Long mid : mids) {
                        userSum = userSum.add(LearnRecordService.progressOf(recMap.get(uid + ":" + mid)));
                    }
                    sum = sum.add(userSum.divide(BigDecimal.valueOf(mids.size()), 2, RoundingMode.HALF_UP));
                }
                avgProgressMap.put(t.getId(),
                        sum.divide(BigDecimal.valueOf(uids.size()), 1, RoundingMode.HALF_UP));
            }
        }
        // 目标单位名称 + 创建人姓名
        Set<Long> orgIds = new LinkedHashSet<>();
        Set<Long> creatorIds = new LinkedHashSet<>();
        for (LearnTask t : tasks) {
            orgIds.addAll(scopeOrgIds(t.getTargetScope()));
            if (t.getCreateBy() != null) {
                creatorIds.add(t.getCreateBy());
            }
        }
        Map<Long, String> orgNames = orgUnitService.mapUnitNames(orgIds);
        Map<Long, String> creatorNames = new HashMap<>();
        if (!creatorIds.isEmpty()) {
            for (OrgUser u : orgUserService.listByIds(creatorIds)) {
                creatorNames.put(u.getId(), u.getUserName());
            }
        }

        return PageResult.of(page).map(t -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", t.getId());
            item.put("title", t.getTaskName());
            item.put("materialCount", t.getMaterialIds() == null ? 0 : t.getMaterialIds().size());
            item.put("requiredSec", t.getRequiredDurationSec());
            item.put("deadline", t.getDeadline());
            List<String> names = new ArrayList<>();
            for (Long orgId : scopeOrgIds(t.getTargetScope())) {
                names.add(orgNames.getOrDefault(orgId, String.valueOf(orgId)));
            }
            item.put("targetOrgNames", names);
            long[] stat = stats.getOrDefault(t.getId(), new long[]{0, 0});
            item.put("assigneeCount", stat[0]);
            item.put("doneCount", stat[1]);
            item.put("avgProgress", avgProgressMap.getOrDefault(t.getId(), BigDecimal.ZERO));
            item.put("createdBy", creatorNames.get(t.getCreateBy()));
            item.put("createdTime", t.getCreateTime());
            return item;
        });
    }

    // ---------------------------------------------------------------- LEARN-06 本人学习任务待办

    public PageResult<Map<String, Object>> myTasks(String status, long pageNum, long pageSize) {
        Long userId = SecurityUtils.getUserId();
        QueryWrapper<LearnTaskUser> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        if ("DONE".equals(status)) {
            qw.eq("complete_status", 1);
        } else if ("TODO".equals(status)) {
            qw.eq("complete_status", 0);
        }
        qw.orderByDesc("create_time");
        Page<LearnTaskUser> page = taskUserMapper.selectPage(new Page<>(pageNum, pageSize), qw);

        List<LearnTaskUser> rows = page.getRecords();
        Map<Long, LearnTask> taskMap = new HashMap<>();
        if (!rows.isEmpty()) {
            for (LearnTask t : list(new QueryWrapper<LearnTask>()
                    .in("id", rows.stream().map(LearnTaskUser::getTaskId).collect(Collectors.toSet())))) {
                taskMap.put(t.getId(), t);
            }
        }
        Set<Long> materialIds = new LinkedHashSet<>();
        for (LearnTask t : taskMap.values()) {
            if (t.getMaterialIds() != null) {
                materialIds.addAll(t.getMaterialIds());
            }
        }
        Map<Long, LearnMaterial> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            for (LearnMaterial m : materialService.listByIds(materialIds)) {
                materialMap.put(m.getId(), m);
            }
        }
        Map<Long, LearnRecord> recordMap = recordService.mapByMaterialIds(userId, materialIds);

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> items = new ArrayList<>();
        for (LearnTaskUser row : rows) {
            LearnTask task = taskMap.get(row.getTaskId());
            if (task == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", task.getId());
            item.put("title", task.getTaskName());
            item.put("materialIds", task.getMaterialIds());
            List<Map<String, Object>> materials = new ArrayList<>();
            for (Long mid : task.getMaterialIds() == null ? List.<Long>of() : task.getMaterialIds()) {
                LearnMaterial m = materialMap.get(mid);
                if (m == null) {
                    continue;
                }
                Map<String, Object> mi = new LinkedHashMap<>();
                mi.put("materialId", m.getId());
                mi.put("title", m.getTitle());
                mi.put("materialType", LearnMaterialService.materialTypeOf(m.getFileType()));
                mi.put("durationSec", m.getDurationSec());
                mi.put("myProgressPct", LearnRecordService.progressOf(recordMap.get(mid)));
                materials.add(mi);
            }
            item.put("materials", materials);
            item.put("requiredSec", task.getRequiredDurationSec());
            item.put("deadline", task.getDeadline());
            String myStatus;
            if (row.getCompleteStatus() != null && row.getCompleteStatus() == 1) {
                myStatus = "DONE";
            } else if (task.getDeadline() != null && task.getDeadline().isBefore(now)) {
                myStatus = "EXPIRED";
            } else {
                myStatus = "TODO";
            }
            item.put("myStatus", myStatus);
            item.put("completedTime", row.getCompleteTime());
            items.add(item);
        }
        return PageResult.of(page.getTotal(), items, pageNum, pageSize);
    }

    // ---------------------------------------------------------------- 资料完成后回刷任务人员状态

    /**
     * 资料完成后，重算包含该资料的本人未完成任务：
     * 任务内全部资料 finish_status=2 且这些资料累计有效时长 ≥ required_duration_sec → complete_status=1。
     */
    public void refreshOnMaterialFinish(Long userId, Long materialId) {
        List<LearnTaskUser> pending = taskUserMapper.selectList(new QueryWrapper<LearnTaskUser>()
                .eq("user_id", userId).eq("complete_status", 0));
        if (pending.isEmpty()) {
            return;
        }
        Map<Long, LearnTask> taskMap = new HashMap<>();
        for (LearnTask t : list(new QueryWrapper<LearnTask>()
                .in("id", pending.stream().map(LearnTaskUser::getTaskId).collect(Collectors.toSet())))) {
            taskMap.put(t.getId(), t);
        }
        LocalDateTime now = LocalDateTime.now();
        for (LearnTaskUser row : pending) {
            LearnTask task = taskMap.get(row.getTaskId());
            if (task == null || task.getMaterialIds() == null || !task.getMaterialIds().contains(materialId)) {
                continue;
            }
            Map<Long, LearnRecord> records = recordService.mapByMaterialIds(userId, task.getMaterialIds());
            boolean allFinished = true;
            long totalEffective = 0;
            for (Long mid : task.getMaterialIds()) {
                LearnRecord r = records.get(mid);
                if (r == null || r.getFinishStatus() == null || r.getFinishStatus() != 2) {
                    allFinished = false;
                    break;
                }
                totalEffective += r.getEffectiveDurationSec() == null ? 0 : r.getEffectiveDurationSec();
            }
            if (allFinished && totalEffective >= (task.getRequiredDurationSec() == null ? 0 : task.getRequiredDurationSec())) {
                row.setCompleteStatus(1);
                row.setCompleteTime(now);
                taskUserMapper.updateById(row);
            }
        }
    }

    // ---------------------------------------------------------------- 内部工具

    /** target_scope.orgPaths 末段还原单位id */
    static List<Long> scopeOrgIds(Map<String, Object> targetScope) {
        List<Long> ids = new ArrayList<>();
        if (targetScope == null) {
            return ids;
        }
        Object paths = targetScope.get("orgPaths");
        if (paths instanceof List<?> list) {
            for (Object p : list) {
                if (p == null) {
                    continue;
                }
                String path = p.toString();
                String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
                int idx = trimmed.lastIndexOf('/');
                if (idx >= 0) {
                    try {
                        ids.add(Long.parseLong(trimmed.substring(idx + 1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return ids;
    }
}
