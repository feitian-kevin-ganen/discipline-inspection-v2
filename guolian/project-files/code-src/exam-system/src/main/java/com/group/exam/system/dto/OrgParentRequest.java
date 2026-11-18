package com.group.exam.system.dto;

import jakarta.validation.constraints.NotNull;

/**
 * ORG-07 调整单位归属请求
 */
public class OrgParentRequest {

    @NotNull(message = "newParentId 不能为空")
    private Long newParentId;
    public Long getNewParentId() {
        return newParentId;
    }

    public void setNewParentId(Long newParentId) {
        this.newParentId = newParentId;
    }
}
