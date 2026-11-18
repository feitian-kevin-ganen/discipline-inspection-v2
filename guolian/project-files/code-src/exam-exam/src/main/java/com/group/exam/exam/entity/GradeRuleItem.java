package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 等级规则分数区间（t_grade_rule_item，无 deleted 字段）。
 * 默认：优[90,100] 良[80,90) 中[60,80) 差[0,60)；左闭右开、末段右闭。
 */
@Data
@TableName(value = "t_grade_rule_item", autoResultMap = true)
public class GradeRuleItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private String gradeCode;

    private String gradeName;

    private BigDecimal minScore;

    private BigDecimal maxScore;

    /** 下边界是否闭区间：1含 0不含 */
    private Integer lowerInclusive;

    /** 上边界是否闭区间：1含 0不含 */
    private Integer upperInclusive;

    private Integer sortNo;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
