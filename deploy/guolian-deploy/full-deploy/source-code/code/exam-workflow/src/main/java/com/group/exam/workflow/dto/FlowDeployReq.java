package com.group.exam.workflow.dto;

import lombok.Data;

@Data
public class FlowDeployReq {

    /** 部署名称，同时作为 BPMN 资源名 */
    private String name;
    /** BPMN 2.0 XML 内容 */
    private String xml;
}
