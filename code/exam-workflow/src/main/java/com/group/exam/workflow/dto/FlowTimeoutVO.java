package com.group.exam.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FlowTimeoutVO {

    private String taskId;
    private String taskName;
    private String procInstId;
    private String title;
    private String assignee;
    private String assigneeName;
    private LocalDateTime createTime;
    private Integer timeoutHours;
    private Long overHours;
}
