package com.group.exam.question.enums;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;

/**
 * 题目审核动作（QUES-13）：ENABLE 启用 / REJECT 驳回至草稿 / DISABLE 停用
 */
public enum AuditActionEnum {

    ENABLE,
    REJECT,
    DISABLE;

    public static AuditActionEnum parse(String action) {
        if (action != null) {
            for (AuditActionEnum a : values()) {
                if (a.name().equalsIgnoreCase(action.trim())) {
                    return a;
                }
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "审核动作无法识别: " + action);
    }
}
