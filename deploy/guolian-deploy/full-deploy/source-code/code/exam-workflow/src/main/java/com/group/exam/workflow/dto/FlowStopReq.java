package com.group.exam.workflow.dto;

import lombok.Data;

@Data
public class FlowStopReq {

    /** 终止原因（可空） */
    private String comment;
}
