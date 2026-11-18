package com.group.exam.question.enums;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;

/**
 * 题型：DB 编码 1-5 ↔ API 编码 SINGLE/MULTI/JUDGE/FILL/ESSAY
 */
public enum QuesTypeEnum {

    SINGLE(1, "SINGLE", "单选"),
    MULTI(2, "MULTI", "多选"),
    JUDGE(3, "JUDGE", "判断"),
    FILL(4, "FILL", "填空"),
    ESSAY(5, "ESSAY", "简答");

    private final int code;
    private final String apiCode;
    private final String cnName;

    QuesTypeEnum(int code, String apiCode, String cnName) {
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

    public String getCnName() {
        return this.cnName;
    }

    public static QuesTypeEnum ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (QuesTypeEnum t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }

    /** 解析 API 编码，兼容中文名（导入用）；非法抛 10001 */
    public static QuesTypeEnum parse(String apiCodeOrCn) {
        QuesTypeEnum t = parseOrNull(apiCodeOrCn);
        if (t == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "题型无法识别: " + apiCodeOrCn);
        }
        return t;
    }

    public static QuesTypeEnum parseOrNull(String apiCodeOrCn) {
        if (apiCodeOrCn == null || apiCodeOrCn.isBlank()) {
            return null;
        }
        String s = apiCodeOrCn.trim();
        for (QuesTypeEnum t : values()) {
            if (t.apiCode.equalsIgnoreCase(s) || t.cnName.equals(s)) {
                return t;
            }
        }
        // 兼容"单选题/多选题/判断题/填空题/简答题"写法
        if (s.endsWith("题")) {
            String stripped = s.substring(0, s.length() - 1);
            for (QuesTypeEnum t : values()) {
                if (t.cnName.equals(stripped)) {
                    return t;
                }
            }
        }
        return null;
    }

    public static String toApi(Integer code) {
        QuesTypeEnum t = ofCode(code);
        return t == null ? null : t.apiCode;
    }

    /** 是否为选择题型（单选/多选） */
    public boolean isChoice() {
        return this == SINGLE || this == MULTI;
    }
}
