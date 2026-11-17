package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 提交复核结论（MARK-06）：reviewId 即 t_answer_detail.id（简化，无独立复核表）
 */
@Data
public class ReviewSubmitReq implements Serializable {

    private Long reviewId;

    private BigDecimal finalScore;

    private String comment;
}
