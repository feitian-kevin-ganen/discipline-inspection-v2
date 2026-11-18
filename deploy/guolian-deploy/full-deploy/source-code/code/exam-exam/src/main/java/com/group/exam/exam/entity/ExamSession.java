package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 考试场次（t_exam_session）
 *
 * <p>session_status：0未开始 1进行中 2已结束 3成绩已发布 4已取消；
 * session_kind：1正考 2补考。
 * 说明：考试须知 notice 无独立列，存于 paper_strategy_snapshot.notice。
 */
@Data
@TableName(value = "t_exam_session", autoResultMap = true)
public class ExamSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionName;

    private Long paperId;

    private String paperName;

    private Integer paperVersion;

    /** 试卷策略快照：{paperType,totalScore,passScore,multiJudgeRule,samePaper,randomStrategy,gradeRuleId,gradeRuleItems,notice} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> paperStrategySnapshot;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMin;

    private Integer lateLimitMin;

    private Integer switchLimit;

    /** 是否允许提前交卷：1是 0否 */
    private Integer allowEarlySubmit;

    private Integer earlySubmitAfterMin;

    /** 试题乱序：1开 0关 */
    private Integer shuffleQuestion;

    /** 选项乱序：1开 0关 */
    private Integer shuffleOption;

    /** 0未开始 1进行中 2已结束 3成绩已发布 4已取消 */
    private Integer sessionStatus;

    private LocalDateTime scorePublishTime;

    private Long relatedSessionId;

    /** 1正考 2补考 */
    private Integer sessionKind;

    private Long createOrgId;

    private String createOrgPath;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
