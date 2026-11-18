package com.group.exam.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class FlowInstanceDetailVO {

    private FlowInstanceVO instance;
    private List<FlowHistoryVO> history;
}
