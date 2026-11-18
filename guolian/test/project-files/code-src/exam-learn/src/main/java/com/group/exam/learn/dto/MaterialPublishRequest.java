package com.group.exam.learn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * LIB-11 发布/下架
 */
@Data
public class MaterialPublishRequest {

    /** PUBLISH / OFFLINE */
    @NotBlank(message = "action 不能为空")
    private String action;
}
