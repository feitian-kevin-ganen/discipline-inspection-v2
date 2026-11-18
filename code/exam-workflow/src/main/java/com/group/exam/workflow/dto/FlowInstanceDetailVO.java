package com.group.exam.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FlowInstanceDetailVO {

    private FlowInstanceVO instance;
    private List<FlowHistoryVO> history;
    /** 流程变量（history-level=audit 时含发起参数） */
    private Map<String, Object> variables;
}
