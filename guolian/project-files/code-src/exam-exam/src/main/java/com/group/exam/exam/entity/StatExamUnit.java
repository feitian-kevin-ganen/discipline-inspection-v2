package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 场次×单位预聚合统计快照（t_stat_exam_unit，无 deleted 字段）。
 * 成绩发布时由 exam-exam 写入，exam-stat 模块读取。
 */
@Data
@TableName(value = "t_stat_exam_unit", autoResultMap = true)
public class StatExamUnit implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long orgId;

    private String orgPath;

    private String orgName;

    private Integer orgLevel;

    private Integer shouldCount;

    private Integer actualCount;

    private Integer absentCount;

    private BigDecimal avgScore;

    private BigDecimal maxScore;

    private BigDecimal minScore;

    private Integer passCount;

    /** 及格率% */
    private BigDecimal passRate;

    /** 等级分布：{"优":12,"良":30,...} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> gradeDist;

    /** 分数段分布：{"0-59":2,"60-69":8,...} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> segmentDist;

    private LocalDateTime statTime;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
