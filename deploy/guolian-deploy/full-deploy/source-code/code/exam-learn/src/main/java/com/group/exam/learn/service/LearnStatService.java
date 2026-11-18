package com.group.exam.learn.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.learn.entity.LearnRecord;
import com.group.exam.learn.entity.LearnTask;
import com.group.exam.learn.entity.LearnTaskUser;
import com.group.exam.learn.mapper.LearnRecordMapper;
import com.group.exam.learn.mapper.LearnTaskMapper;
import com.group.exam.learn.mapper.LearnTaskUserMapper;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.OrgUserService;
import com.group.exam.system.service.SysConfigService;
import com.group.exam.system.service.SysLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习统计服务（LSTAT-01~05）。时间范围过滤口径：t_learn_record.last_learn_time 落在周期内。
 */
@Service
public class LearnStatService {

    private final LearnRecordMapper recordMapper;
    private final LearnTaskMapper taskMapper;
    private final LearnTaskUserMapper taskUserMapper;
    private final OrgUnitService orgUnitService;
    private final OrgUserService orgUserService;
    private final LearnCategoryService categoryService;
    private final SysConfigService sysConfigService;
    private final SysLogService sysLogService;

    public LearnStatService(LearnRecordMapper recordMapper, LearnTaskMapper taskMapper,
                            LearnTaskUserMapper taskUserMapper, OrgUnitService orgUnitService,
                            OrgUserService orgUserService, LearnCategoryService categoryService,
                            SysConfigService sysConfigService, SysLogService sysLogService) {
        this.recordMapper = recordMapper;
        this.taskMapper = taskMapper;
        this.taskUserMapper = taskUserMapper;
        this.orgUnitService = orgUnitService;
        this.orgUserService = orgUserService;
        this.categoryService = categoryService;
        this.sysConfigService = sysConfigService;
        this.sysLogService = sysLogService;
    }

    // ---------------------------------------------------------------- LSTAT-01 个人学习统计

    public Map<String, Object> personal(String periodType, String startDate, String endDate) {
        Long userId = SecurityUtils.getUserId();
        LocalDateTime[] period = resolvePeriod(periodType, startDate, endDate);
        List<LearnRecord> records = recordMapper.selectList(new QueryWrapper<LearnRecord>()
                .eq("user_id", userId)
                .ge("last_learn_time", period[0]).lt("last_learn_time", period[1]));

        long totalEffectiveSec = 0;
        int completed = 0, inProgress = 0, notStarted = 0;
        Map<Long, long[]> byCat = new LinkedHashMap<>(); // catId → [effectiveSec, completedCount]
        for (LearnRecord r : records) {
            int sec = r.getEffectiveDurationSec() == null ? 0 : r.getEffectiveDurationSec();
            totalEffectiveSec += sec;
            int st = r.getFinishStatus() == null ? 0 : r.getFinishStatus();
            if (st == 2) {
                completed++;
            } else if (st == 1) {
                inProgress++;
            } else {
                notStarted++;
            }
            if (r.getCategoryId() != null) {
                long[] agg = byCat.computeIfAbsent(r.getCategoryId(), k -> new long[2]);
                agg[0] += sec;
                if (st == 2) {
                    agg[1]++;
                }
            }
        }
        Map<Long, String> catNames = categoryService.mapCatNames(byCat.keySet());
        List<Map<String, Object>> categoryDist = new ArrayList<>();
        byCat.forEach((catId, agg) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("catId", catId);
            item.put("catName", catNames.get(catId));
            item.put("effectiveSec", agg[0]);
            item.put("completedCount", agg[1]);
            categoryDist.add(item);
        });

        Map<String, Object> materialStats = new LinkedHashMap<>();
        materialStats.put("completed", completed);
        materialStats.put("inProgress", inProgress);
        materialStats.put("notStarted", notStarted);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalEffectiveSec", totalEffectiveSec);
        data.put("materialStats", materialStats);
        data.put("categoryDist", categoryDist);
        data.put("taskStats", taskStatsOf(List.of(userId)).get(userId));
        return data;
    }

    // ---------------------------------------------------------------- LSTAT-02/03 单位汇总与下钻

    public Map<String, Object> orgSummary(Long orgId, String periodType, String startDate, String endDate) {
        OrgUnit unit = requireUnitInScope(orgId);
        LocalDateTime[] period = resolvePeriod(periodType, startDate, endDate);
        List<OrgUnit> children = orgUnitService.list(new QueryWrapper<OrgUnit>()
                .eq("parent_id", unit.getId()).orderByAsc("sort_no").orderByAsc("id"));

        int standardSec = sysConfigService.getInt("learn.standardSec", 3600);
        List<Map<String, Object>> childItems = new ArrayList<>();
        for (OrgUnit child : children) {
            childItems.add(aggregateOrg(child, period, standardSec));
        }
        boolean hasSubLevel = !children.isEmpty() && orgUnitService.count(new QueryWrapper<OrgUnit>()
                .in("parent_id", children.stream().map(OrgUnit::getId).collect(Collectors.toList()))) > 0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orgId", unit.getId());
        data.put("orgName", unit.getUnitName());
        data.put("children", childItems);
        data.put("hasSubLevel", hasSubLevel);
        return data;
    }

    /** LSTAT-03 detailType=USER：单位人员明细分页（含未达标标记） */
    public PageResult<Map<String, Object>> orgUserDetail(Long orgId, String periodType, String startDate,
                                                         String endDate, long pageNum, long pageSize) {
        OrgUnit unit = requireUnitInScope(orgId);
        LocalDateTime[] period = resolvePeriod(periodType, startDate, endDate);
        Page<OrgUser> page = orgUserService.page(new Page<>(pageNum, pageSize), new QueryWrapper<OrgUser>()
                .likeRight("org_path", unit.getPath())
                .ne("account_status", 0)
                .orderByAsc("id"));
        List<OrgUser> users = page.getRecords();
        Map<Long, long[]> agg = aggregateByUser(
                users.stream().map(OrgUser::getId).collect(Collectors.toList()), period);
        Map<Long, Map<String, Object>> taskStats = taskStatsOf(
                users.stream().map(OrgUser::getId).collect(Collectors.toList()));
        int standardSec = sysConfigService.getInt("learn.standardSec", 3600);

        List<Map<String, Object>> items = new ArrayList<>();
        for (OrgUser u : users) {
            items.add(userDetailItem(u, agg.getOrDefault(u.getId(), new long[3]),
                    taskStats.get(u.getId()), standardSec));
        }
        return PageResult.of(page.getTotal(), items, pageNum, pageSize);
    }

    // ---------------------------------------------------------------- LSTAT-04 学习排行

    public List<Map<String, Object>> ranking(String rankType, Long orgId, String periodType,
                                             String startDate, String endDate, Integer topN) {
        int limit = topN == null ? 20 : topN;
        if (limit < 1 || limit > 100) {
            throw new BizException(ErrorCode.STAT_PARAM_ERROR, "topN 取值范围 1~100");
        }
        OrgUnit unit = requireUnitInScope(orgId);
        LocalDateTime[] period = resolvePeriod(periodType, startDate, endDate);
        List<Map<String, Object>> result = new ArrayList<>();

        if ("ORG".equals(rankType)) {
            List<OrgUnit> children = orgUnitService.list(new QueryWrapper<OrgUnit>()
                    .eq("parent_id", unit.getId()).orderByAsc("sort_no").orderByAsc("id"));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (OrgUnit child : children) {
                List<LearnRecord> records = recordsInPath(child.getPath(), period);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orgId", child.getId());
                row.put("orgName", child.getUnitName());
                row.put("effectiveSec", records.stream().mapToLong(
                        r -> r.getEffectiveDurationSec() == null ? 0 : r.getEffectiveDurationSec()).sum());
                row.put("completedCount", records.stream().filter(
                        r -> r.getFinishStatus() != null && r.getFinishStatus() == 2).count());
                rows.add(row);
            }
            rows.sort(Comparator.comparingLong(r -> -((Number) r.get("effectiveSec")).longValue()));
            int rank = 0;
            for (Map<String, Object> row : rows) {
                if (rank >= limit) {
                    break;
                }
                row.put("rank", ++rank);
                result.add(row);
            }
        } else if ("USER".equals(rankType)) {
            Map<Long, List<LearnRecord>> byUser = recordsInPath(unit.getPath(), period).stream()
                    .collect(Collectors.groupingBy(LearnRecord::getUserId, LinkedHashMap::new, Collectors.toList()));
            List<Map<String, Object>> rows = new ArrayList<>();
            byUser.forEach((uid, list) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", uid);
                row.put("name", list.get(0).getUserName());
                row.put("orgName", list.get(0).getOrgName());
                row.put("effectiveSec", list.stream().mapToLong(
                        r -> r.getEffectiveDurationSec() == null ? 0 : r.getEffectiveDurationSec()).sum());
                row.put("completedCount", list.stream().filter(
                        r -> r.getFinishStatus() != null && r.getFinishStatus() == 2).count());
                rows.add(row);
            });
            rows.sort(Comparator.comparingLong(r -> -((Number) r.get("effectiveSec")).longValue()));
            int rank = 0;
            for (Map<String, Object> row : rows) {
                if (rank >= limit) {
                    break;
                }
                row.put("rank", ++rank);
                result.add(row);
            }
        } else {
            throw new BizException(ErrorCode.STAT_PARAM_ERROR, "rankType 仅支持 ORG/USER");
        }
        return result;
    }

    // ---------------------------------------------------------------- LSTAT-05 导出

    public void export(String exportType, Long orgId, String periodType, String startDate, String endDate,
                       OutputStream out) {
        OrgUnit unit = requireUnitInScope(orgId);
        LocalDateTime[] period = resolvePeriod(periodType, startDate, endDate);
        int standardSec = sysConfigService.getInt("learn.standardSec", 3600);
        List<List<String>> head;
        List<List<Object>> rows = new ArrayList<>();

        switch (exportType == null ? "" : exportType) {
            case "ORG_SUMMARY" -> {
                head = List.of(List.of("单位ID"), List.of("单位名称"), List.of("在册人数"),
                        List.of("人均有效时长(秒)"), List.of("资料完成率(%)"), List.of("任务完成率(%)"), List.of("未达标人数"));
                List<OrgUnit> children = orgUnitService.list(new QueryWrapper<OrgUnit>()
                        .eq("parent_id", unit.getId()).orderByAsc("sort_no").orderByAsc("id"));
                for (OrgUnit child : children) {
                    Map<String, Object> agg = aggregateOrg(child, period, standardSec);
                    rows.add(List.of(agg.get("orgId"), agg.get("orgName"), agg.get("userCount"),
                            agg.get("avgEffectiveSec"), agg.get("materialDoneRate"), agg.get("taskDoneRate"),
                            agg.get("belowCount")));
                }
            }
            case "USER_DETAIL", "BELOW_STANDARD" -> {
                head = List.of(List.of("用户ID"), List.of("姓名"), List.of("工号"), List.of("单位"),
                        List.of("有效时长(秒)"), List.of("完成资料数"), List.of("进行中资料数"), List.of("是否未达标"));
                List<OrgUser> users = orgUserService.list(new QueryWrapper<OrgUser>()
                        .likeRight("org_path", unit.getPath()).ne("account_status", 0).orderByAsc("id"));
                Map<Long, long[]> agg = aggregateByUser(
                        users.stream().map(OrgUser::getId).collect(Collectors.toList()), period);
                for (OrgUser u : users) {
                    long[] a = agg.getOrDefault(u.getId(), new long[3]);
                    boolean below = a[0] < standardSec;
                    if ("BELOW_STANDARD".equals(exportType) && !below) {
                        continue;
                    }
                    rows.add(List.of(u.getId(), u.getUserName(), u.getJobNo(),
                            orgUnitService.mapUnitNames(List.of(u.getOrgId())).getOrDefault(u.getOrgId(), ""),
                            a[0], a[1], a[2], below ? "是" : "否"));
                }
            }
            default -> throw new BizException(ErrorCode.STAT_PARAM_ERROR,
                    "exportType 仅支持 ORG_SUMMARY/USER_DETAIL/BELOW_STANDARD");
        }
        try {
            EasyExcel.write(out).head(head).sheet("学习统计").doWrite(rows);
        } catch (Exception e) {
            throw new BizException(ErrorCode.EXPORT_FAILED, "导出失败: " + e.getMessage());
        }
        sysLogService.log(1, "LSTAT", "学习统计导出",
                "exportType=" + exportType + "，orgId=" + unit.getId() + "，行数=" + rows.size(), 1);
    }

    // ---------------------------------------------------------------- 内部聚合

    /** 单位子树聚合：userCount/avgEffectiveSec/materialDoneRate/taskDoneRate/belowCount */
    private Map<String, Object> aggregateOrg(OrgUnit child, LocalDateTime[] period, int standardSec) {
        List<LearnRecord> records = recordsInPath(child.getPath(), period);
        long totalSec = 0;
        long doneCount = 0;
        Map<Long, Long> secByUser = new HashMap<>();
        for (LearnRecord r : records) {
            long sec = r.getEffectiveDurationSec() == null ? 0 : r.getEffectiveDurationSec();
            totalSec += sec;
            if (r.getFinishStatus() != null && r.getFinishStatus() == 2) {
                doneCount++;
            }
            secByUser.merge(r.getUserId(), sec, Long::sum);
        }
        long userCount = orgUserService.count(new QueryWrapper<OrgUser>()
                .likeRight("org_path", child.getPath()).ne("account_status", 0));
        long meeting = secByUser.values().stream().filter(s -> s >= standardSec).count();
        long belowCount = Math.max(0, userCount - meeting);

        // 任务完成率：该单位子树快照行 complete_status 均值
        List<LearnTaskUser> taskUsers = taskUserMapper.selectList(new QueryWrapper<LearnTaskUser>()
                .select("complete_status").likeRight("org_path", child.getPath()));
        long taskDone = taskUsers.stream().filter(t -> t.getCompleteStatus() != null && t.getCompleteStatus() == 1).count();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orgId", child.getId());
        item.put("orgName", child.getUnitName());
        item.put("userCount", userCount);
        item.put("avgEffectiveSec", userCount == 0 ? 0 : totalSec / userCount);
        item.put("materialDoneRate", records.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(doneCount * 100.0 / records.size()).setScale(2, RoundingMode.HALF_UP));
        item.put("taskDoneRate", taskUsers.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(taskDone * 100.0 / taskUsers.size()).setScale(2, RoundingMode.HALF_UP));
        item.put("belowCount", belowCount);
        return item;
    }

    private List<LearnRecord> recordsInPath(String orgPath, LocalDateTime[] period) {
        return recordMapper.selectList(new QueryWrapper<LearnRecord>()
                .likeRight("org_path", orgPath)
                .ge("last_learn_time", period[0]).lt("last_learn_time", period[1]));
    }

    /** userId → [effectiveSec, completedCount, inProgressCount] */
    private Map<Long, long[]> aggregateByUser(List<Long> userIds, LocalDateTime[] period) {
        Map<Long, long[]> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        List<LearnRecord> records = recordMapper.selectList(new QueryWrapper<LearnRecord>()
                .in("user_id", userIds)
                .ge("last_learn_time", period[0]).lt("last_learn_time", period[1]));
        for (LearnRecord r : records) {
            long[] agg = result.computeIfAbsent(r.getUserId(), k -> new long[3]);
            agg[0] += r.getEffectiveDurationSec() == null ? 0 : r.getEffectiveDurationSec();
            if (r.getFinishStatus() != null && r.getFinishStatus() == 2) {
                agg[1]++;
            } else if (r.getFinishStatus() != null && r.getFinishStatus() == 1) {
                agg[2]++;
            }
        }
        return result;
    }

    /** userId → {total, done, doing, expired, doneRate} */
    private Map<Long, Map<String, Object>> taskStatsOf(List<Long> userIds) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        List<LearnTaskUser> rows = taskUserMapper.selectList(new QueryWrapper<LearnTaskUser>()
                .in("user_id", userIds));
        Map<Long, LearnTask> taskMap = new HashMap<>();
        if (!rows.isEmpty()) {
            for (LearnTask t : taskMapper.selectList(new QueryWrapper<LearnTask>()
                    .in("id", rows.stream().map(LearnTaskUser::getTaskId).collect(Collectors.toSet())))) {
                taskMap.put(t.getId(), t);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Map<Long, List<LearnTaskUser>> byUser = rows.stream()
                .collect(Collectors.groupingBy(LearnTaskUser::getUserId));
        byUser.forEach((uid, list) -> {
            int total = 0, done = 0, doing = 0, expired = 0;
            for (LearnTaskUser row : list) {
                LearnTask task = taskMap.get(row.getTaskId());
                if (task == null) {
                    continue;
                }
                total++;
                if (row.getCompleteStatus() != null && row.getCompleteStatus() == 1) {
                    done++;
                } else if (task.getDeadline() != null && task.getDeadline().isBefore(now)) {
                    expired++;
                } else {
                    doing++;
                }
            }
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", total);
            stats.put("done", done);
            stats.put("doing", doing);
            stats.put("expired", expired);
            stats.put("doneRate", total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(done * 100.0 / total).setScale(2, RoundingMode.HALF_UP));
            result.put(uid, stats);
        });
        return result;
    }

    private Map<String, Object> userDetailItem(OrgUser u, long[] agg, Map<String, Object> taskStats,
                                               int standardSec) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("userId", u.getId());
        item.put("name", u.getUserName());
        item.put("jobNo", u.getJobNo());
        item.put("effectiveSec", agg[0]);
        item.put("completedCount", agg[1]);
        item.put("inProgressCount", agg[2]);
        item.put("taskDoneRate", taskStats == null ? BigDecimal.ZERO : taskStats.get("doneRate"));
        item.put("isBelowStandard", agg[0] < standardSec);
        return item;
    }

    // ---------------------------------------------------------------- 内部工具

    /** orgId 为空时取数据权限根节点（超管取全系统根单位）；越权抛 10004 */
    private OrgUnit requireUnitInScope(Long orgId) {
        LoginUser current = SecurityUtils.getLoginUser();
        Long id = orgId;
        if (id == null) {
            if (current.isSuperAdmin()) {
                OrgUnit root = orgUnitService.getOne(new QueryWrapper<OrgUnit>()
                        .isNull("parent_id").orderByAsc("id").last("LIMIT 1"), false);
                if (root == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "组织单位不存在");
                }
                return root;
            }
            id = current.getOrgId();
        }
        OrgUnit unit = orgUnitService.getUnit(id);
        if (unit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织单位不存在");
        }
        SecurityUtils.checkOrgScope(unit.getPath());
        return unit;
    }

    /** 时间范围：[start, end)；默认当前月度 */
    private LocalDateTime[] resolvePeriod(String periodType, String startDate, String endDate) {
        String type = StringUtils.hasText(periodType) ? periodType : "MONTH";
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        switch (type) {
            case "MONTH" -> {
                return new LocalDateTime[]{today.withDayOfMonth(1).atStartOfDay(), LocalDateTime.now()};
            }
            case "QUARTER" -> {
                int firstMonth = (today.getMonthValue() - 1) / 3 * 3 + 1;
                return new LocalDateTime[]{
                        LocalDate.of(today.getYear(), firstMonth, 1).atStartOfDay(), LocalDateTime.now()};
            }
            case "YEAR" -> {
                return new LocalDateTime[]{today.withDayOfYear(1).atStartOfDay(), LocalDateTime.now()};
            }
            case "CUSTOM" -> {
                if (!StringUtils.hasText(startDate) || !StringUtils.hasText(endDate)) {
                    throw new BizException(ErrorCode.STAT_PARAM_ERROR, "CUSTOM 时 startDate/endDate 必填");
                }
                try {
                    return new LocalDateTime[]{LocalDate.parse(startDate, fmt).atStartOfDay(),
                            LocalDate.parse(endDate, fmt).plusDays(1).atStartOfDay()};
                } catch (Exception e) {
                    throw new BizException(ErrorCode.STAT_PARAM_ERROR, "日期格式须为 yyyy-MM-dd");
                }
            }
            default -> throw new BizException(ErrorCode.STAT_PARAM_ERROR, "非法 periodType: " + periodType);
        }
    }
}
