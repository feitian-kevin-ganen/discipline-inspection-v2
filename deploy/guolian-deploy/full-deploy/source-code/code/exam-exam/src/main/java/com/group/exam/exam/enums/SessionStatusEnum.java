package com.group.exam.exam.enums;

/**
 * 场次状态：0未开始 1进行中 2已结束 3成绩已发布 4已取消
 */
public enum SessionStatusEnum {

    NOT_STARTED(0, "NOT_STARTED"),
    IN_PROGRESS(1, "IN_PROGRESS"),
    ENDED(2, "ENDED"),
    SCORED_PUBLISHED(3, "SCORED_PUBLISHED"),
    CANCELED(4, "CANCELED");

    private final int code;
    private final String apiCode;

    SessionStatusEnum(int code, String apiCode) {
        this.code = code;
        this.apiCode = apiCode;
    }

    public int getCode() {
        return this.code;
    }

    public String getApiCode() {
        return this.apiCode;
    }

    public static SessionStatusEnum ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SessionStatusEnum s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }

    public static SessionStatusEnum parseOrNull(String apiCode) {
        if (apiCode == null || apiCode.isBlank()) {
            return null;
        }
        for (SessionStatusEnum s : values()) {
            if (s.apiCode.equalsIgnoreCase(apiCode.trim())) {
                return s;
            }
        }
        return null;
    }

    public static String toApi(Integer code) {
        SessionStatusEnum s = ofCode(code);
        return s == null ? null : s.apiCode;
    }
}
