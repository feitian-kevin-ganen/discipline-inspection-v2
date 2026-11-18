package com.group.exam.learn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * LIB-12 设置可见范围
 */
@Data
public class MaterialScopeRequest {

    /** ALL / L2_SUB / SPECIFIC */
    @NotBlank(message = "scopeType 不能为空")
    private String scopeType;

    /** 可见单位id（scopeType≠ALL 时必填） */
    private List<Long> scopeOrgIds;
}
