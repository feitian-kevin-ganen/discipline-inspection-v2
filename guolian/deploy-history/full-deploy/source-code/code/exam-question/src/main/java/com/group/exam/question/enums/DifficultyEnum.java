package com.group.exam.question.enums;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;

/**
 * 难度：DB 编码 1-3 ↔ API 编码 EASY/MEDIUM/HARD
 */
public enum DifficultyEnum {

    EASY(1, "EASY", "易"),
    MEDIUM(2, "MEDIUM", "中"),
    HARD(3, "HARD", "难");

    private final int code;
    private final String apiCode;
    private final String cnName;

    DifficultyEnum(int code, String apiCode, String cnName) {
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

    public static DifficultyEnum ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DifficultyEnum d : values()) {
            if (d.code == code) {
                return d;
            }
        }
        return null;
    }

    /** 解析 API 编码，兼容中文名（导入用）；非法抛 10001 */
    public static DifficultyEnum parse(String apiCodeOrCn) {
        DifficultyEnum d = parseOrNull(apiCodeOrCn);
        if (d == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "难度无法识别: " + apiCodeOrCn);
        }
        return d;
    }

    public static DifficultyEnum parseOrNull(String apiCodeOrCn) {
        if (apiCodeOrCn == null || apiCodeOrCn.isBlank()) {
            return null;
        }
        String s = apiCodeOrCn.trim();
        for (DifficultyEnum d : values()) {
            if (d.apiCode.equalsIgnoreCase(s) || d.cnName.equals(s)) {
                return d;
            }
        }
        // 兼容常见同义写法
        return switch (s) {
            case "简单", "容易", "低" -> EASY;
            case "中等", "普通", "适中" -> MEDIUM;
            case "困难", "高" -> HARD;
            default -> null;
        };
    }

    public static String toApi(Integer code) {
        DifficultyEnum d = ofCode(code);
        return d == null ? null : d.apiCode;
    }
}
