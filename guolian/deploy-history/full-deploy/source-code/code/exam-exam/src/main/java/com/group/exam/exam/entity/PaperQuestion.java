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
 * 固定卷题目与分值快照（t_paper_question，无 deleted 字段）
 */
@Data
@TableName(value = "t_paper_question", autoResultMap = true)
public class PaperQuestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long quesId;

    /** 1单选 2多选 3判断 4填空 5简答 */
    private Integer quesType;

    /** 本题在本卷的分值 */
    private BigDecimal score;

    private Integer sortNo;

    /** 题目快照 {stem,options,answer,analysis,difficulty,quesType} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> quesSnapshot;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
