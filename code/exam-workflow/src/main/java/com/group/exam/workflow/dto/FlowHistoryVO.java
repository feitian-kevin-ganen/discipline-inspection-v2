package com.group.exam.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FlowHistoryVO {

    private String taskId;
    private String taskName;
    private String assignee;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String comment;
}
