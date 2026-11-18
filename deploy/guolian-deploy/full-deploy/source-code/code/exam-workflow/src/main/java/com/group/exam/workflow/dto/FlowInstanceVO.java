package com.group.exam.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FlowInstanceVO {

    private Long id;
    private String bizType;
    private Long bizId;
    private String procInstId;
    private String procDefKey;
    private String title;
    private Long startUserId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
