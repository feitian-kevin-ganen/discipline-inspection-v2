package com.group.exam.workflow.dto;

import lombok.Data;

@Data
public class FlowMessageTriggerReq {

    /** flow_message_registry.message_ref，对应 BPMN messageEventDefinition 的 messageRef */
    private String messageRef;
    /** 可选：只触发指定流程实例，留空时广播全部等待中的实例 */
    private String procInstId;
}
