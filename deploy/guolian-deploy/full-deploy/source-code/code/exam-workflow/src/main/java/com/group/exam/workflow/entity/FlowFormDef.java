package com.group.exam.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 流程表单模板 */
@Data
@TableName("flow_form_def")
public class FlowFormDef implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String formKey;
    private String name;
    /** 字段定义 JSON 数组 */
    private String schemaJson;
    private String procDefKey;
    private Integer version;
    /** 0=启用 1=停用 */
    private String status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
