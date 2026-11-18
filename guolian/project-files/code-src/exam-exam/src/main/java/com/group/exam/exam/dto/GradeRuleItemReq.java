package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 等级规则区间项：{gradeName, minScore, maxScore}（左闭右开、末段右闭由服务端展开）
 */
@Data
public class GradeRuleItemReq implements Serializable {

    private String gradeName;

    private BigDecimal minScore;

    private BigDecimal maxScore;
}
