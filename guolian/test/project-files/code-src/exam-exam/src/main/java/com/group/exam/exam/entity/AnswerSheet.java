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
 * 答卷主表（t_answer_sheet，无 deleted 字段，一人一场次一份）
 *
 * <p>sheet_status：1答题中 2已交卷；submit_type：1主动 2到时自动 3强制收卷 4切屏超限；
 * mark_status：0答题中 1待判分 2客观已判 3阅卷中 4成绩已合成 5已发布。
 */
@Data
@TableName(value = "t_answer_sheet", autoResultMap = true)
public class AnswerSheet implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long candidateId;

    private Long userId;

    private String userName;

    private String jobNo;

    private Long orgId;

    private String orgPath;

    private String orgName;

    private Long paperId;

    /** 考生实际试卷快照 {paperId,paperName,totalScore,passScore,multiJudgeRule,gradeRuleId,gradeRuleItems,questions:[...]} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> paperSnapshot;

    /** 进入测试时刻 */
    private LocalDateTime startTime;

    /** 服务端计时锚点 = min(进入时刻+时长, 场次窗口结束) */
    private LocalDateTime serverDeadline;

    private LocalDateTime submitTime;

    /** 答题用时快照(秒,V1.1)：交卷时写入=submit-start(超时按场限时截断)，排名"同分按用时升序"依据 */
    private Integer durationSec;

    /** 1主动 2到时自动 3强制收卷 4切屏超限 */
    private Integer submitType;

    private Integer switchCount;

    private String lastIp;

    private String terminal;

    private String userAgent;

    /** 答题会话令牌，单终端在线控制 */
    private String sessionToken;

    private BigDecimal objectiveScore;

    private BigDecimal subjectiveScore;

    private BigDecimal totalScore;

    private Long gradeRuleId;

    private String gradeCode;

    private String gradeName;

    /** 0答题中 1待判分 2客观已判 3阅卷中 4成绩已合成 5已发布 */
    private Integer markStatus;

    /** 1答题中 2已交卷 */
    private Integer sheetStatus;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
