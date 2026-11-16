package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 编辑试卷（PAPER-04），仅传需修改字段。
 * 未封存且未被考试引用的试卷允许修改抽题策略与分数设计（randomStrategy/totalScore 等）
 */
@Data
public class PaperUpdateReq implements Serializable {

    private String paperName;

    private BigDecimal totalScore;

    private BigDecimal passScore;

    /** ALL_OR_NOTHING / HALF_ON_MISS / NONE_ON_MISS */
    private String multiScoreRule;

    /** 随机卷抽题策略（全量替换）：[{quesType, bankId/bankIds, includeChildren, difficulty, count, score}] */
    private List<Map<String, Object>> randomStrategy;

    /** 随机卷是否全员同卷 */
    private Boolean samePaperForAll;
}
