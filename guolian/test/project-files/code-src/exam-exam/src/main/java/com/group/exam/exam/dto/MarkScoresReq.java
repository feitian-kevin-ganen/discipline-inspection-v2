package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量提交评分（MARK-04）
 */
@Data
public class MarkScoresReq implements Serializable {

    private Long taskId;

    private List<ScoreItem> scores;

    @Data
    public static class ScoreItem implements Serializable {
        private Long detailId;
        private BigDecimal score;
        private String comment;
    }
}
