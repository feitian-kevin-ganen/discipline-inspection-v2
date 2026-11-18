package com.group.exam.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.mapper.OrgUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户分组（GROUP-01~04，V1.1 新增）
 *
 * <p>分组为人员标签字段口径：t_org_user.user_group（SMALLINT），固定枚举无独立分组表——
 * 1派驻组 2子企业纪委 3纪检委员 9其他纪检相关人员（默认）。分组与组织树正交；
 * 数据权限按 org_path 前缀过滤，设置/移出仅允许操作人数据权限范围内的人员（越权计入 skipped）。
 */
@Service
public class UserGroupService extends ServiceImpl<OrgUserMapper, OrgUser> {

    /** 固定分组枚举：1派驻组 2子企业纪委 3纪检委员 9其他纪检相关人员 */
    private static final Map<Integer, String> GROUP_NAMES = new LinkedHashMap<>();

    /** 默认分组：9 其他 */
    public static final int GROUP_OTHER = 9;

    static {
        GROUP_NAMES.put(1, "派驻组");
        GROUP_NAMES.put(2, "子企业纪委");
        GROUP_NAMES.put(3, "纪检委员");
        GROUP_NAMES.put(GROUP_OTHER, "其他纪检相关人员");
    }

    private final OrgUnitService orgUnitService;
    private final SysLogService sysLogService;

    public UserGroupService(OrgUnitService orgUnitService, SysLogService sysLogService) {
        this.orgUnitService = orgUnitService;
        this.sysLogService = sysLogService;
    }

    /** 校验分组标签合法（1/2/3/9），非法抛 10001 */
    public static int requireValidGroup(Integer userGroup) {
        if (userGroup == null || !GROUP_NAMES.containsKey(userGroup)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "分组标签非法（仅支持 1派驻组/2子企业纪委/3纪检委员/9其他）: " + userGroup);
        }
        return userGroup;
    }

    // ---------------------------------------------------------------- GROUP-01 分组清单（含各分组人数，按数据权限过滤）

    public List<Map<String, Object>> listGroups() {
        String prefix = SecurityUtils.dataScopePathPrefix();
        QueryWrapper<OrgUser> qw = new QueryWrapper<OrgUser>()
                .select("user_group", "COUNT(*) AS cnt")
                .ne("account_status", 0)
                .groupBy("user_group");
        qw.likeRight(prefix != null, "org_path", prefix);
        Map<Integer, Long> counts = new HashMap<>();
        for (Map<String, Object> row : listMaps(qw)) {
            Object group = row.get("user_group");
            if (group instanceof Number n) {
                counts.put(n.intValue(), ((Number) row.get("cnt")).longValue());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        GROUP_NAMES.forEach((group, name) -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("userGroup", group);
            m.put("groupName", name);
            m.put("memberCount", counts.getOrDefault(group, 0L));
            result.add(m);
        });
        return result;
    }

    // ---------------------------------------------------------------- GROUP-02 分组成员分页

    public PageResult<Map<String, Object>> pageMembers(Integer userGroup, String keyword,
                                                       long pageNum, long pageSize) {
        int group = requireValidGroup(userGroup);
        QueryWrapper<OrgUser> qw = new QueryWrapper<>();
        String prefix = SecurityUtils.dataScopePathPrefix();
        qw.likeRight(prefix != null, "org_path", prefix);
        qw.eq("user_group", group);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("user_name", keyword.trim()).or().like("job_no", keyword.trim()));
        }
        qw.orderByDesc("create_time");
        Page<OrgUser> page = page(new Page<>(pageNum, pageSize), qw);
        return PageResult.of(page).map(this::toMemberItem);
    }

    private Map<String, Object> toMemberItem(OrgUser user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("userId", user.getId());
        item.put("name", user.getUserName());
        item.put("jobNo", user.getJobNo());
        OrgUnit unit = user.getOrgId() == null ? null : orgUnitService.getUnit(user.getOrgId());
        item.put("orgName", unit == null ? null : unit.getUnitName());
        item.put("position", user.getPosition());
        item.put("userGroup", user.getUserGroup() == null ? GROUP_OTHER : user.getUserGroup());
        item.put("status", user.getAccountStatus());
        return item;
    }

    // ---------------------------------------------------------------- GROUP-03 批量设置分组标签

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> assign(Integer userGroup, List<Long> userIds) {
        int group = requireValidGroup(userGroup);
        List<TargetUser> targets = resolveInScope(userIds);
        int updated = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (TargetUser target : targets) {
            if (target.error() != null) {
                skipped.add(skipItem(target.userId(), target.error()));
                continue;
            }
            OrgUser update = new OrgUser();
            update.setId(target.user().getId());
            update.setUserGroup(group);
            update.setUpdateBy(SecurityUtils.getUserId());
            updateById(update);
            updated++;
        }
        if (updated > 0) {
            sysLogService.log(2, "USER", "批量设置分组",
                    "设置分组[" + GROUP_NAMES.get(group) + "] " + updated + " 人，跳过 " + skipped.size() + " 人", 1);
        }
        return batchResult(updated, skipped);
    }

    // ---------------------------------------------------------------- GROUP-04 批量移出分组（重置为 9 其他）

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> remove(Integer userGroup, List<Long> userIds) {
        int group = requireValidGroup(userGroup);
        List<TargetUser> targets = resolveInScope(userIds);
        int updated = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (TargetUser target : targets) {
            if (target.error() != null) {
                skipped.add(skipItem(target.userId(), target.error()));
                continue;
            }
            // 仅处理当前确属该分组的用户，其余计入 skipped
            int current = target.user().getUserGroup() == null ? GROUP_OTHER : target.user().getUserGroup();
            if (current != group) {
                skipped.add(skipItem(target.userId(), "该用户当前不属于此分组"));
                continue;
            }
            OrgUser update = new OrgUser();
            update.setId(target.user().getId());
            update.setUserGroup(GROUP_OTHER);
            update.setUpdateBy(SecurityUtils.getUserId());
            updateById(update);
            updated++;
        }
        if (updated > 0) {
            sysLogService.log(2, "USER", "批量移出分组",
                    "移出分组[" + GROUP_NAMES.get(group) + "] " + updated + " 人，跳过 " + skipped.size() + " 人", 1);
        }
        return batchResult(updated, skipped);
    }

    // ---------------------------------------------------------------- 内部工具

    /** 目标用户解析结果：user 为 null 且 error 为 null 表示用户不存在 */
    private record TargetUser(Long userId, OrgUser user, String error) {
    }

    /** 批量解析目标用户：逐个校验存在性与数据权限（越权不中断，记入 error 由调用方计入 skipped） */
    private List<TargetUser> resolveInScope(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "userIds 不能为空");
        }
        List<TargetUser> targets = new ArrayList<>();
        for (Long userId : userIds.stream().distinct().toList()) {
            if (userId == null) {
                continue;
            }
            OrgUser user = getById(userId);
            if (user == null) {
                targets.add(new TargetUser(userId, null, "用户不存在"));
            } else if (!SecurityUtils.inOrgScope(user.getOrgPath())) {
                targets.add(new TargetUser(userId, user, "超出数据权限范围"));
            } else {
                targets.add(new TargetUser(userId, user, null));
            }
        }
        return targets;
    }

    private static Map<String, Object> skipItem(Long userId, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("reason", reason);
        return m;
    }

    private static Map<String, Object> batchResult(int updated, List<Map<String, Object>> skipped) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedCount", updated);
        result.put("skipped", skipped);
        return result;
    }
}
