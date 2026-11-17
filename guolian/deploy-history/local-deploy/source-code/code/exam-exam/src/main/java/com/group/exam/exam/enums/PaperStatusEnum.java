package com.group.exam.exam.enums;

/**
 * 试卷状态：0草稿(EDITING) 1启用(READY) 2封存(SEALED)
 */
public enum PaperStatusEnum {

    EDITING(0, "EDITING"),
    READY(1, "READY"),
    SEALED(2, "SEALED");

    private final int code;
    private final String apiCode;

    PaperStatusEnum(int code, String apiCode) {
        this.code = code;
        this.apiCode = apiCode;
    }

    public int getCode() {
        return this.code;
    }

    public String getApiCode() {
        return this.apiCode;
    }

    public static PaperStatusEnum ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaperStatusEnum s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }

    public static PaperStatusEnum parseOrNull(String apiCode) {
        if (apiCode == null || apiCode.isBlank()) {
            return null;
        }
        for (PaperStatusEnum s : values()) {
            if (s.apiCode.equalsIgnoreCase(apiCode.trim())) {
                return s;
            }
        }
        return null;
    }

    public static String toApi(Integer code) {
        PaperStatusEnum s = ofCode(code);
        return s == null ? null : s.apiCode;
    }
}
