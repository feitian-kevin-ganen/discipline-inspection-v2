package com.group.exam.system.dto;

import jakarta.validation.constraints.NotNull;

/**
 * USER-06 账号启用/锁定/解锁请求
 */
public class UserStatusRequest {

    /** 1正常 0停用 2锁定 */
    @NotNull(message = "status 不能为空")
    private Integer status;
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
