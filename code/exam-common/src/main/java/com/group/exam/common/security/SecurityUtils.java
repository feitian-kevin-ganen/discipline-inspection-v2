package com.group.exam.common.security;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 登录上下文与数据权限工具
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户，未登录抛 10002
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    public static boolean hasRole(String roleCode) {
        return getLoginUser().hasRole(roleCode);
    }

    public static boolean isSuperAdmin() {
        return getLoginUser().isSuperAdmin();
    }

    /**
     * 要求当前用户具备任一指定角色，否则抛 10003
     */
    public static void requireAnyRole(String... roleCodes) {
        LoginUser user = getLoginUser();
        for (String roleCode : roleCodes) {
            if (user.hasRole(roleCode)) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    /**
     * 数据权限 org_path 前缀：超管返回 null（不过滤），其他返回本人 orgPath。
     * 用法：queryWrapper.likeRight(prefix != null, "org_path", prefix)
     */
    public static String dataScopePathPrefix() {
        LoginUser user = getLoginUser();
        return user.isSuperAdmin() ? null : user.getOrgPath();
    }

    /**
     * 校验目标数据是否在本级组织范围内（path 前缀匹配），越权抛 10004
     */
    public static void checkOrgScope(String targetOrgPath) {
        LoginUser user = getLoginUser();
        if (user.isSuperAdmin()) {
            return;
        }
        if (targetOrgPath == null || user.getOrgPath() == null || !targetOrgPath.startsWith(user.getOrgPath())) {
            throw new BizException(ErrorCode.DATA_SCOPE_DENIED);
        }
    }

    /**
     * 校验目标 orgId 的路径是否在本级范围内（已知目标 path 时同 checkOrgScope）
     */
    public static boolean inOrgScope(String targetOrgPath) {
        LoginUser user = getLoginUser();
        if (user.isSuperAdmin()) {
            return true;
        }
        return targetOrgPath != null && user.getOrgPath() != null && targetOrgPath.startsWith(user.getOrgPath());
    }
}
