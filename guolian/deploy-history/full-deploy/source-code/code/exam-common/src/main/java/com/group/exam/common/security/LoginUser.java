package com.group.exam.common.security;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录用户上下文（JWT 载荷映射）
 */
@Data
public class LoginUser implements Serializable {

    private Long userId;
    private String jobNo;
    private String userName;
    private Long orgId;
    /** 所属单位物化路径，如 /1/3/，数据权限过滤依据 */
    private String orgPath;
    /** 角色编码列表，如 SUPER_ADMIN / HQ_ADMIN / EXAMINEE */
    private List<String> roles;
    /** 数据权限范围：1全部 2本节点及下级 3仅本人（取本人所有角色的最小值） */
    private Integer dataScope;

    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }

    /** 是否为管理端角色（可访问 /admin/**） */
    public boolean isAdminSide() {
        if (roles == null) {
            return false;
        }
        return roles.stream().anyMatch(r -> switch (r) {
            case "SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN", "MARKER" -> true;
            default -> false;
        });
    }

    /** 超级管理员（数据权限不受限） */
    public boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN") || (dataScope != null && dataScope == 1);
    }
}
