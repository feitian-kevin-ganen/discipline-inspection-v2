package com.group.exam.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FlowCopyVO {

    private Long id;
    private String procInstId;
    private String taskDefKey;
    private String taskName;
    private String title;
    private Long fromUserId;
    private String fromUserName;
    private Long toUserId;
    private String readFlag;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
}
