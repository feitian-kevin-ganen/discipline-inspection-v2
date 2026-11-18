package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbListMapHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 试卷（t_paper）
 *
 * <p>paper_type：1固定卷 2随机卷；paper_status：0草稿(EDITING) 1启用(READY) 2封存(SEALED)；
 * multi_judge_rule：1全对得分 2漏选半分 3漏选不得分；same_paper：随机卷全考生同卷 1是 0一人一卷。
 */
@Data
@TableName(value = "t_paper", autoResultMap = true)
public class Paper implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paperName;

    /** 1固定卷 2随机卷 */
    private Integer paperType;

    private BigDecimal totalScore;

    private BigDecimal passScore;

    /** 多选判分：1全对得分 2漏选半分 3漏选不得分 */
    private Integer multiJudgeRule;

    /** 随机抽题策略：[{quesType,bankId/bankIds,includeChildren,difficulty,count,score}] */
    @TableField(typeHandler = JsonbListMapHandler.class)
    private List<Map<String, Object>> randomStrategy;

    /** 随机卷全考生同卷：1是 0一人一卷 */
    private Integer samePaper;

    private Long gradeRuleId;

    /** 0草稿 1启用 2封存 */
    private Integer paperStatus;

    private Integer version;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
