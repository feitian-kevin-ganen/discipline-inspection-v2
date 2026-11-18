package com.group.exam.workflow.dto;

import lombok.Data;

@Data
public class FlowDefinitionStateReq {

    /** true=挂起（新实例不可发起），false=激活 */
    private boolean suspend;
}
