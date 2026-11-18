package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 配置等级规则（PAPER-10）
 */
@Data
public class PaperGradeRulesReq implements Serializable {

    private List<GradeRuleItemReq> gradeRules;
}
