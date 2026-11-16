package com.group.exam.exam.support;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.question.enums.DifficultyEnum;
import com.group.exam.question.enums.QuesTypeEnum;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 随机抽题策略解析：[{quesType, bankId/bankIds, includeChildren, difficulty, count, score}]
 * （API 文档 4.8 PAPER-02 字段为准，兼容 bankIds/includeChildren 写法）
 */
public final class RandomStrategy {

    private RandomStrategy() {
    }

    public record Rule(List<Long> bankIds, boolean includeChildren, Integer quesType,
                       Integer difficulty, int count, BigDecimal score) {
    }

    /** 解析并校验策略条目，非法抛 10001 */
    public static List<Rule> parse(List<Map<String, Object>> strategy) {
        if (strategy == null || strategy.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "随机卷必须配置 randomStrategy");
        }
        List<Rule> rules = new ArrayList<>();
        for (Map<String, Object> raw : strategy) {
            if (raw == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "抽题策略条目不能为空");
            }
            QuesTypeEnum type = QuesTypeEnum.parseOrNull(asString(raw.get("quesType")));
            if (type == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "抽题策略题型无法识别: " + raw.get("quesType"));
            }
            List<Long> bankIds = new ArrayList<>();
            Object bankIdsRaw = raw.get("bankIds");
            if (bankIdsRaw instanceof List<?> list) {
                for (Object o : list) {
                    Long id = asLong(o);
                    if (id != null) {
                        bankIds.add(id);
                    }
                }
            }
            Long singleBankId = asLong(raw.get("bankId"));
            if (singleBankId != null && !bankIds.contains(singleBankId)) {
                bankIds.add(singleBankId);
            }
            if (bankIds.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "抽题策略缺少 bankId/bankIds");
            }
            Object withChildren = raw.containsKey("includeChildren") ? raw.get("includeChildren") : raw.get("withChildren");
            boolean includeChildren = withChildren == null || Boolean.parseBoolean(String.valueOf(withChildren));
            DifficultyEnum diff = DifficultyEnum.parseOrNull(asString(raw.get("difficulty")));
            int count = asInt(raw.get("count"), -1);
            if (count <= 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "抽题策略 count 必须为正整数");
            }
            BigDecimal score = asBigDecimal(raw.get("score"));
            if (score == null || score.signum() <= 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "抽题策略 score 必须为正数");
            }
            rules.add(new Rule(List.copyOf(bankIds), includeChildren, type.getCode(),
                    diff == null ? null : diff.getCode(), count, score));
        }
        return rules;
    }

    public static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int asInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static BigDecimal asBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
