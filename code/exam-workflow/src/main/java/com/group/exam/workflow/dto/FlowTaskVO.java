package com.group.exam.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FlowTaskVO {

    private String taskId;
    private String taskName;
    private String procInstId;
    private String procDefKey;
    private String procDefName;
    private String bizType;
    private Long bizId;
    private String bizTitle;
    private String assignee;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
