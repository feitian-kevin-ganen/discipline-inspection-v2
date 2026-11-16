package com.group.exam.question.enums;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;

/**
 * 题目状态：DB 编码 0-3 ↔ API 编码 DRAFT/PENDING_AUDIT/ENABLED/DISABLED
 */
public enum ItemStatusEnum {

    DRAFT(0, "DRAFT"),
    PENDING_AUDIT(1, "PENDING_AUDIT"),
    ENABLED(2, "ENABLED"),
    DISABLED(3, "DISABLED");

    private final int code;
    private final String apiCode;

    ItemStatusEnum(int code, String apiCode) {
        this.code = code;
        this.apiCode = apiCode;
    }

    public int getCode() {
        return this.code;
    }

    public String getApiCode() {
        return this.apiCode;
    }

    public static ItemStatusEnum ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ItemStatusEnum s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }

    /** 解析 API 编码；非法抛 10001 */
    public static ItemStatusEnum parse(String apiCode) {
        if (apiCode == null || apiCode.isBlank()) {
            return null;
        }
        String s = apiCode.trim();
        for (ItemStatusEnum st : values()) {
            if (st.apiCode.equalsIgnoreCase(s)) {
                return st;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "题目状态无法识别: " + apiCode);
    }

    public static String toApi(Integer code) {
        ItemStatusEnum s = ofCode(code);
        return s == null ? null : s.apiCode;
    }
}
