package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 等级规则（t_grade_rule）
 */
@Data
@TableName(value = "t_grade_rule", autoResultMap = true)
public class GradeRule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;

    private BigDecimal fullScore;

    /** 是否系统默认规则：1是 0否 */
    private Integer isDefault;

    private Integer status;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
