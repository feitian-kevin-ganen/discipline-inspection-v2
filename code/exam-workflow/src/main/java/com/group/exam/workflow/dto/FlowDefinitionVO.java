package com.group.exam.workflow.dto;

import lombok.Data;

@Data
public class FlowDefinitionVO {

    private String id;
    private String key;
    private String name;
    private int version;
    private boolean suspended;
}
