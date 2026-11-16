package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 考生名单快照（t_exam_candidate）
 *
 * <p>candidate_status：0未考 1已交卷 2缺考 3强制收卷；source：1范围生成 2手工追加；剔除走 deleted=1。
 */
@Data
@TableName(value = "t_exam_candidate", autoResultMap = true)
public class ExamCandidate implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String userName;

    private String jobNo;

    private Long orgId;

    private String orgPath;

    private String orgName;

    /** 0未考 1已交卷 2缺考 3强制收卷 */
    private Integer candidateStatus;

    /** 1范围生成 2手工追加 */
    private Integer source;

    /** 0未通知 1已通知 */
    private Integer notifyStatus;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
