package com.group.exam.learn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * LIB-10 资料审核
 */
@Data
public class MaterialAuditRequest {

    /** APPROVE / REJECT */
    @NotBlank(message = "action 不能为空")
    private String action;

    /** 驳回时必填审核意见 */
    private String comment;
}
