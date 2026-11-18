package com.group.exam.system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * ORG-06 同级单位排序请求
 */
public class OrgSortRequest {

    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    @NotEmpty(message = "orderedOrgIds 不能为空")
    private List<Long> orderedOrgIds;
    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public List<Long> getOrderedOrgIds() {
        return orderedOrgIds;
    }

    public void setOrderedOrgIds(List<Long> orderedOrgIds) {
        this.orderedOrgIds = orderedOrgIds;
    }
}
