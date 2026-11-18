package com.group.exam.question.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 题目审核请求（QUES-13）
 */
public class AuditReq implements Serializable {

    /** ENABLE 启用 / REJECT 驳回至草稿 / DISABLE 停用 */
    @NotBlank(message = "action 不能为空")
    private String action;

    private String comment;

    public String getAction() {
        return this.action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return this.comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
