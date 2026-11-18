package com.group.exam.exam.enums;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;

/**
 * 多选判分规则：DB 1/2/3 ↔ API ALL_OR_NOTHING/HALF_ON_MISS/NONE_ON_MISS
 */
public enum MultiScoreRuleEnum {

    ALL_OR_NOTHING(1, "ALL_OR_NOTHING", "全对得分"),
    HALF_ON_MISS(2, "HALF_ON_MISS", "漏选得半分，错选不得分"),
    NONE_ON_MISS(3, "NONE_ON_MISS", "错选、漏选均不得分");

    private final int code;
    private final String apiCode;
    private final String cnName;

    MultiScoreRuleEnum(int code, String apiCode, String cnName) {
        this.code = code;
        this.apiCode = apiCode;
        this.cnName = cnName;
    }

    public int getCode() {
        return this.code;
    }

    public String getApiCode() {
        return this.apiCode;
    }

    public static MultiScoreRuleEnum ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MultiScoreRuleEnum r : values()) {
            if (r.code == code) {
                return r;
            }
        }
        return null;
    }

    /** 解析 API 编码，null/空返回 null（调用方给默认值）；非法抛 10001 */
    public static MultiScoreRuleEnum parseOrNull(String apiCode) {
        if (apiCode == null || apiCode.isBlank()) {
            return null;
        }
        for (MultiScoreRuleEnum r : values()) {
            if (r.apiCode.equalsIgnoreCase(apiCode.trim())) {
                return r;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "多选判分规则无法识别: " + apiCode);
    }

    public static String toApi(Integer code) {
        MultiScoreRuleEnum r = ofCode(code);
        return r == null ? null : r.apiCode;
    }
}
