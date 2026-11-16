package com.group.exam.learn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习任务人员快照，表 t_learn_task_user
 */
@Data
@TableName("t_learn_task_user")
public class LearnTaskUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long userId;

    private String userName;

    private Long orgId;

    /** 下达时单位物化路径快照 */
    private String orgPath;

    private String orgName;

    /** 0未完成 1已完成 */
    private Integer completeStatus;

    private LocalDateTime completeTime;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
