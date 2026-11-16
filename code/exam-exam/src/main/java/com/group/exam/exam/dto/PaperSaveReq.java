package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 创建试卷请求（PAPER-02）：paperType FIXED/RANDOM
 */
@Data
public class PaperSaveReq implements Serializable {

    private String paperName;

    /** FIXED / RANDOM */
    private String paperType;

    private BigDecimal totalScore;

    private BigDecimal passScore;

    /** ALL_OR_NOTHING / HALF_ON_MISS / NONE_ON_MISS，默认 HALF_ON_MISS */
    private String multiScoreRule;

    /** RANDOM 专用：true=全考生同卷 */
    private Boolean samePaperForAll;

    /** FIXED 必填：[{itemId, score, seq}] */
    private List<QuestionItem> questions;

    /** RANDOM 必填：[{quesType, bankId/bankIds, includeChildren, difficulty, count, score}] */
    private List<Map<String, Object>> randomStrategy;

    /** 不传则使用系统默认等级规则 */
    private List<GradeRuleItemReq> gradeRules;

    @Data
    public static class QuestionItem implements Serializable {
        private Long itemId;
        private BigDecimal score;
        private Integer seq;
    }
}
