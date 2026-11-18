package com.group.exam.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlowStartReq {

    /** 业务类型 = 流程定义 key */
    @NotBlank(message = "bizType 不能为空")
    private String bizType;

    @NotNull(message = "bizId 不能为空")
    private Long bizId;

    private String title;
}
