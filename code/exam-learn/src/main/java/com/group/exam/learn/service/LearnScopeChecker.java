package com.group.exam.learn.service;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.service.OrgUnitService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习资料/分类可见范围判定（FR-LIB-04）。
 * visible_scope JSONB：{"type":"ALL"} | {"type":"ORG","orgPaths":["/1/3/"],"withChildren":true}
 * API 侧 scopeType：ALL / L2_SUB（指定二级集团及下属）/ SPECIFIC（仅指定单位）。
 * 考生端可见性简化口径：一律按 orgPath 前缀匹配。
 */
@Component
public class LearnScopeChecker {

    public static final String TYPE_ALL = "ALL";
    public static final String TYPE_ORG = "ORG";

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_L2_SUB = "L2_SUB";
    public static final String SCOPE_SPECIFIC = "SPECIFIC";

    private final OrgUnitService orgUnitService;

    public LearnScopeChecker(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    /** 由 API scopeType + scopeOrgIds 构建 visible_scope JSON */
    public Map<String, Object> buildScope(String scopeType, List<Long> scopeOrgIds) {
        if (!StringUtils.hasText(scopeType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "scopeType 不能为空");
        }
        switch (scopeType) {
            case SCOPE_ALL -> {
                Map<String, Object> scope = new HashMap<>();
                scope.put("type", TYPE_ALL);
                return scope;
            }
            case SCOPE_L2_SUB, SCOPE_SPECIFIC -> {
                if (scopeOrgIds == null || scopeOrgIds.isEmpty()) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "scopeType≠ALL 时 scopeOrgIds 必填");
                }
                List<String> orgPaths = new ArrayList<>();
                for (Long orgId : scopeOrgIds) {
                    OrgUnit unit = orgUnitService.getUnit(orgId);
                    if (unit == null) {
                        throw new BizException(ErrorCode.PARAM_ERROR, "可见单位不存在: " + orgId);
                    }
                    orgPaths.add(unit.getPath());
                }
                Map<String, Object> scope = new HashMap<>();
                scope.put("type", TYPE_ORG);
                scope.put("orgPaths", orgPaths);
                scope.put("withChildren", SCOPE_L2_SUB.equals(scopeType));
                return scope;
            }
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法 scopeType: " + scopeType);
        }
    }

    /** visible_scope JSON → API scopeType */
    public String scopeTypeOf(Map<String, Object> scope) {
        if (scope == null || TYPE_ALL.equals(scope.get("type"))) {
            return SCOPE_ALL;
        }
        return Boolean.TRUE.equals(scope.get("withChildren")) ? SCOPE_L2_SUB : SCOPE_SPECIFIC;
    }

    /** visible_scope JSON → API scopeOrgIds（取 orgPath 末段还原单位id） */
    public List<Long> scopeOrgIdsOf(Map<String, Object> scope) {
        List<Long> ids = new ArrayList<>();
        for (String path : orgPathsOf(scope)) {
            String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            int idx = trimmed.lastIndexOf('/');
            if (idx >= 0) {
                try {
                    ids.add(Long.parseLong(trimmed.substring(idx + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ids;
    }

    /** 考生端可见性：ALL 全员可见；ORG 一律前缀匹配（简化口径） */
    public boolean visible(Map<String, Object> scope, String userOrgPath) {
        if (scope == null || TYPE_ALL.equals(scope.get("type"))) {
            return true;
        }
        if (userOrgPath == null) {
            return false;
        }
        for (String path : orgPathsOf(scope)) {
            if (userOrgPath.startsWith(path)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> orgPathsOf(Map<String, Object> scope) {
        if (scope == null) {
            return List.of();
        }
        Object paths = scope.get("orgPaths");
        if (paths instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object p : list) {
                if (p != null) {
                    result.add(p.toString());
                }
            }
            return result;
        }
        return List.of();
    }
}
