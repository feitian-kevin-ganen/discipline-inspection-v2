package com.group.exam.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 催办 / 消息发送记录（本系统只落库，不接外部短信邮件通道） */
@Data
@TableName("flow_notify_log")
public class FlowNotifyLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String procInstId;
    private String taskId;
    private String notifyType;
    private String channel;
    private Long receiverId;
    private String content;
    private String sendStatus;
    private String failReason;
    private LocalDateTime createTime;
}
