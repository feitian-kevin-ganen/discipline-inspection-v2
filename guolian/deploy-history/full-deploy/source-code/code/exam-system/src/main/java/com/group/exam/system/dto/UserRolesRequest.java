package com.group.exam.system.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * USER-09 分配角色请求
 */
public class UserRolesRequest {

    @NotEmpty(message = "roleCodes 不能为空")
    private List<String> roleCodes;
    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes;
    }
}
