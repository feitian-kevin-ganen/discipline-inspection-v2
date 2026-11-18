package com.group.exam.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** BPMN 消息引用注册（用于手动触发等待中的消息事件） */
@Data
@TableName("flow_message_registry")
public class FlowMessageRegistry implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageRef;
    private String channel;
    private String topic;
    private String description;
    private LocalDateTime createTime;
}
