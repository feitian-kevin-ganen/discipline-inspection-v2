package com.group.exam.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务单据与流程实例的绑定关系（其余流转数据由 Flowable ACT_* 表维护）
 */
@Data
@TableName("flow_biz_instance")
public class FlowBizInstance implements Serializable {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_STOPPED = "STOPPED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型，同时作为流程定义 key，如 ques_item_audit / material_audit */
    private String bizType;
    private Long bizId;
    private String procInstId;
    private String procDefKey;
    /** 单据标题，用于待办/我发起的列表展示 */
    private String title;
    private Long startUserId;
    /** RUNNING / APPROVED / REJECTED / STOPPED */
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
