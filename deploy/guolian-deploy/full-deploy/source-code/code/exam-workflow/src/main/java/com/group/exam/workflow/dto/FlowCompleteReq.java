package com.group.exam.workflow.dto;

import lombok.Data;

@Data
public class FlowCompleteReq {

    /** true=同意（流程结束为通过）；false=驳回（流程终止） */
    private boolean approve;

    /** 审批意见；驳回时必填 */
    private String comment;
}
