package com.group.exam.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.dto.ConfigUpdateRequest;
import com.group.exam.system.dto.NoticePublishRequest;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.entity.SysConfig;
import com.group.exam.system.entity.SysLog;
import com.group.exam.system.entity.SysLoginLog;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统管理编排服务（参数/日志查询/通知发布）
 */
@Service
public class SysAdminService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysConfigService sysConfigService;
    private final SysLogService sysLogService;
    private final SysLoginLogService sysLoginLogService;
    private final SysNoticeService sysNoticeService;
    private final OrgUserService orgUserService;
    private final OrgUnitService orgUnitService;

    public SysAdminService(SysConfigService sysConfigService, SysLogService sysLogService,
                           SysLoginLogService sysLoginLogService, SysNoticeService sysNoticeService,
                           OrgUserService orgUserService, OrgUnitService orgUnitService) {
        this.sysConfigService = sysConfigService;
        this.sysLogService = sysLogService;
        this.sysLoginLogService = sysLoginLogService;
        this.sysNoticeService = sysNoticeService;
        this.orgUserService = orgUserService;
        this.orgUnitService = orgUnitService;
    }

    // ---------------------------------------------------------------- SYS-01 参数查询

    public List<Map<String, Object>> listConfigs(String group) {
        QueryWrapper<SysConfig> qw = new QueryWrapper<>();
        if (StringUtils.hasText(group)) {
            qw.eq("config_group", group.toLowerCase());
        }
        qw.orderByAsc("config_group").orderByAsc("id");
        return sysConfigService.list(qw).stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("configKey", c.getConfigKey());
            // 表内无 config_name/default_value 字段：configName 取 remark，defaultValue 返回 null
            item.put("configName", c.getRemark());
            item.put("configValue", c.getConfigValue());
            item.put("valueType", c.getValueType());
            item.put("group", c.getConfigGroup());
            item.put("defaultValue", null);
            item.put("updatedBy", c.getUpdateBy());
            item.put("updatedTime", c.getUpdateTime());
            return item;
        }).toList();
    }

    // ---------------------------------------------------------------- SYS-02 参数批量修改（仅超管，Controller 层已校验角色）

    public void updateConfigs(List<ConfigUpdateRequest.Item> items) {
        Set<String> seen = new HashSet<>();
        for (ConfigUpdateRequest.Item item : items) {
            if (!seen.add(item.getConfigKey())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "configKey 重复: " + item.getConfigKey());
            }
            SysConfig config = sysConfigService.getOne(
                    new QueryWrapper<SysConfig>().eq("config_key", item.getConfigKey()), false);
            if (config == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "参数不存在: " + item.getConfigKey());
            }
            if (config.getEditable() != null && config.getEditable() == 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "参数不允许修改: " + item.getConfigKey());
            }
            validateValueType(config, item.getConfigValue());
            config.setConfigValue(item.getConfigValue());
            config.setUpdateBy(SecurityUtils.getUserId());
            sysConfigService.updateById(config);
        }
        sysLogService.log(1, "SYS", "修改系统参数",
                items.stream().map(i -> i.getConfigKey() + "=" + i.getConfigValue())
                        .collect(Collectors.joining("; ")), 1);
    }

    private void validateValueType(SysConfig config, String value) {
        Integer type = config.getValueType();
        if (type == null) {
            return;
        }
        switch (type) {
            case 2 -> {
                try {
                    Double.parseDouble(value.trim());
                } catch (Exception e) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "参数 " + config.getConfigKey() + " 须为数字");
                }
            }
            case 3 -> {
                if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "参数 " + config.getConfigKey() + " 须为 true/false");
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- SYS-03 登录日志

    public PageResult<Map<String, Object>> pageLoginLogs(String keyword, String result, String startTime,
                                                         String endTime, long pageNum, long pageSize) {
        QueryWrapper<SysLoginLog> qw = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like("job_no", keyword);
        }
        if (StringUtils.hasText(result)) {
            qw.eq("login_result", "SUCCESS".equalsIgnoreCase(result) ? 1 : 0);
        }
        LocalDateTime start = parseTime(startTime, false);
        LocalDateTime end = parseTime(endTime, true);
        qw.ge(start != null, "login_time", start);
        qw.le(end != null, "login_time", end);
        qw.orderByDesc("login_time");
        Page<SysLoginLog> page = sysLoginLogService.page(new Page<>(pageNum, pageSize), qw);

        // 富化姓名与单位
        Set<String> jobNos = page.getRecords().stream().map(SysLoginLog::getJobNo).collect(Collectors.toSet());
        Map<String, OrgUser> userByJobNo = new HashMap<>();
        if (!jobNos.isEmpty()) {
            for (OrgUser u : orgUserService.list(new QueryWrapper<OrgUser>().in("job_no", jobNos))) {
                userByJobNo.put(u.getJobNo(), u);
            }
        }
        Set<Long> orgIds = userByJobNo.values().stream().map(OrgUser::getOrgId).collect(Collectors.toSet());
        Map<Long, String> orgNames = orgUnitService.mapUnitNames(orgIds);

        return PageResult.of(page).map(log -> {
            OrgUser user = userByJobNo.get(log.getJobNo());
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("logId", log.getId());
            item.put("jobNo", log.getJobNo());
            item.put("name", user == null ? null : user.getUserName());
            item.put("orgName", user == null ? null : orgNames.get(user.getOrgId()));
            item.put("terminal", log.getTerminal());
            item.put("ip", log.getLoginIp());
            item.put("result", log.getLoginResult() != null && log.getLoginResult() == 1 ? "SUCCESS" : "FAIL");
            item.put("failReason", log.getFailReason());
            item.put("loginTime", log.getLoginTime());
            return item;
        });
    }

    // ---------------------------------------------------------------- SYS-04 操作日志

    public PageResult<Map<String, Object>> pageOperationLogs(String keyword, String module, String startTime,
                                                             String endTime, long pageNum, long pageSize) {
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.in("log_type", 1, 2);
        if (StringUtils.hasText(keyword)) {
            qw.like("user_name", keyword);
        }
        if (StringUtils.hasText(module)) {
            qw.eq("module", module.toUpperCase());
        }
        LocalDateTime start = parseTime(startTime, false);
        LocalDateTime end = parseTime(endTime, true);
        qw.ge(start != null, "create_time", start);
        qw.le(end != null, "create_time", end);
        qw.orderByDesc("create_time");
        Page<SysLog> page = sysLogService.page(new Page<>(pageNum, pageSize), qw);
        Map<Long, String> orgNames = resolveOrgNames(page.getRecords());
        return PageResult.of(page).map(log -> {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("logId", log.getId());
            item.put("operatorName", log.getUserName());
            item.put("orgName", orgNames.get(lastOrgId(log.getOrgPath())));
            item.put("module", log.getModule());
            item.put("action", log.getOperation());
            item.put("targetDesc", null);
            item.put("detail", log.getContent());
            item.put("ip", log.getIp());
            item.put("operateTime", log.getCreateTime());
            return item;
        });
    }

    // ---------------------------------------------------------------- SYS-05 考试异常日志

    public PageResult<Map<String, Object>> pageExamAbnormalLogs(Long examId, String abnormalType,
                                                                long pageNum, long pageSize) {
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.eq("log_type", 3);
        if (examId != null) {
            // 异常日志明细（content）中模糊匹配场次 id
            qw.like("content", String.valueOf(examId));
        }
        if (StringUtils.hasText(abnormalType)) {
            qw.eq("operation", abnormalType.toUpperCase());
        }
        String prefix = SecurityUtils.dataScopePathPrefix();
        qw.likeRight(prefix != null, "org_path", prefix);
        qw.orderByDesc("create_time");
        Page<SysLog> page = sysLogService.page(new Page<>(pageNum, pageSize), qw);
        return PageResult.of(page).map(log -> {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("logId", log.getId());
            item.put("examName", null);
            item.put("jobNo", null);
            item.put("name", log.getUserName());
            item.put("abnormalType", log.getOperation());
            item.put("detail", log.getContent());
            item.put("occurTime", log.getCreateTime());
            return item;
        });
    }

    // ---------------------------------------------------------------- SYS-06 通知分页（管理端，按批次聚合）

    public PageResult<Map<String, Object>> pageNotices(String noticeType, long pageNum, long pageSize) {
        Integer type = null;
        if (StringUtils.hasText(noticeType)) {
            type = SysNoticeService.toNoticeType(noticeType.toUpperCase());
            if (type == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "非法通知类型: " + noticeType);
            }
        }
        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        IPage<Map<String, Object>> grouped = sysNoticeService.getBaseMapper().pageGrouped(page, type);

        Set<Long> operatorIds = grouped.getRecords().stream()
                .map(r -> r.get("create_by")).filter(java.util.Objects::nonNull)
                .map(v -> ((Number) v).longValue()).collect(Collectors.toSet());
        Map<Long, String> operatorNames = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            for (OrgUser u : orgUserService.listByIds(operatorIds)) {
                operatorNames.put(u.getId(), u.getUserName());
            }
        }
        PageResult<Map<String, Object>> result = PageResult.of(
                grouped.getTotal(), grouped.getRecords(), grouped.getCurrent(), grouped.getSize());
        return result.map(row -> {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("noticeId", ((Number) row.get("id")).longValue());
            item.put("noticeType", SysNoticeService.toNoticeTypeCode(((Number) row.get("notice_type")).intValue()));
            item.put("title", row.get("title"));
            item.put("targetDesc", null);
            item.put("sentCount", ((Number) row.get("sent_count")).longValue());
            item.put("readCount", ((Number) row.get("read_count")).longValue());
            Object createBy = row.get("create_by");
            item.put("createdBy", createBy == null ? null : operatorNames.get(((Number) createBy).longValue()));
            item.put("createdTime", row.get("create_time"));
            return item;
        });
    }

    // ---------------------------------------------------------------- SYS-07 发布通知

    public Map<String, Object> publishNotice(NoticePublishRequest req) {
        Integer type = SysNoticeService.toNoticeType(req.getNoticeType().toUpperCase());
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法通知类型: " + req.getNoticeType());
        }
        boolean withChildren = req.getWithChildren() == null || req.getWithChildren();
        Set<Long> receiverIds = new LinkedHashSet<>();

        // 未指定目标单位/接收人时按"发送给全体人员"处理（与前端占位提示口径一致）
        boolean noTarget = (req.getTargetOrgIds() == null || req.getTargetOrgIds().isEmpty())
                && (req.getTargetUserIds() == null || req.getTargetUserIds().isEmpty());
        if (Boolean.TRUE.equals(req.getSendAll()) || noTarget) {
            for (OrgUser u : orgUserService.list(new QueryWrapper<OrgUser>().ne("account_status", 0))) {
                receiverIds.add(u.getId());
            }
        }
        if (req.getTargetOrgIds() != null && !req.getTargetOrgIds().isEmpty()) {
            List<OrgUnit> units = orgUnitService.listUnitsByIds(req.getTargetOrgIds());
            if (units.size() != new HashSet<>(req.getTargetOrgIds()).size()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "存在无效的目标单位");
            }
            List<String> paths = units.stream().map(u -> {
                SecurityUtils.checkOrgScope(u.getPath());
                return u.getPath();
            }).toList();
            for (OrgUser u : orgUserService.listActiveByOrgPaths(paths, withChildren)) {
                receiverIds.add(u.getId());
            }
        }
        if (req.getTargetUserIds() != null && !req.getTargetUserIds().isEmpty()) {
            for (OrgUser u : orgUserService.listByIds(req.getTargetUserIds())) {
                SecurityUtils.checkOrgScope(u.getOrgPath());
                receiverIds.add(u.getId());
            }
        }
        if (receiverIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目标范围内无接收人");
        }

        List<Long> ids = sysNoticeService.sendBatch(type, req.getTitle(), req.getContent(), null, null, receiverIds);
        sysLogService.log(1, "SYS", "发布通知",
                "标题[" + req.getTitle() + "]，类型[" + req.getNoticeType() + "]，接收 " + ids.size() + " 人", 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noticeId", ids.get(0));
        result.put("sentCount", ids.size());
        return result;
    }

    // ---------------------------------------------------------------- 内部工具

    private LocalDateTime parseTime(String text, boolean endOfDay) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        try {
            if (trimmed.length() == 10) {
                return LocalDateTime.parse(trimmed + (endOfDay ? " 23:59:59" : " 00:00:00"), DT);
            }
            return LocalDateTime.parse(trimmed, DT);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "时间格式须为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 从物化路径取末段单位 id，如 /1/12/305/ → 305 */
    private Long lastOrgId(String orgPath) {
        if (!StringUtils.hasText(orgPath)) {
            return null;
        }
        String trimmed = orgPath.endsWith("/") ? orgPath.substring(0, orgPath.length() - 1) : orgPath;
        int idx = trimmed.lastIndexOf('/');
        if (idx < 0 || idx == trimmed.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(trimmed.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Long, String> resolveOrgNames(List<SysLog> logs) {
        Set<Long> orgIds = logs.stream().map(l -> lastOrgId(l.getOrgPath()))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return orgUnitService.mapUnitNames(orgIds);
    }
}
