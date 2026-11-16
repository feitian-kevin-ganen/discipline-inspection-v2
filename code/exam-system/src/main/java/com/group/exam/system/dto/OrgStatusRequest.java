package com.group.exam.system.dto;

import jakarta.validation.constraints.NotNull;

/**
 * ORG-05 启用/停用组织单位请求
 */
public class OrgStatusRequest {

    /** 1启用 0停用 */
    @NotNull(message = "status 不能为空")
    private Integer status;
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
