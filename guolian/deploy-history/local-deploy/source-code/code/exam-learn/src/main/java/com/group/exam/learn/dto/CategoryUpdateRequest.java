package com.group.exam.learn.dto;

import lombok.Data;

import java.util.List;

/**
 * LIB-03 编辑分类（含可见范围）
 */
@Data
public class CategoryUpdateRequest {

    private String catName;

    private Integer sortNo;

    /** 可见范围：ALL/L2_SUB/SPECIFIC */
    private String scopeType;

    /** 可见单位id（scopeType≠ALL 时必填） */
    private List<Long> scopeOrgIds;
}
