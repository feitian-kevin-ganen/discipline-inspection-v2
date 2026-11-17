package com.group.exam.learn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * LIB-02 新增分类
 */
@Data
public class CategoryCreateRequest {

    /** 上级分类id，根为 0 */
    private Long parentId;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 128, message = "分类名称过长")
    private String catName;

    private Integer sortNo;
}
