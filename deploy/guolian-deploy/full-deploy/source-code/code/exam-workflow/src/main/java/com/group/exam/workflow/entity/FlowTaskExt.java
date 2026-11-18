package com.group.exam.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 流程节点超时配置 */
@Data
@TableName("flow_task_ext")
public class FlowTaskExt implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String procDefId;
    private String taskDefKey;
    private String taskName;
    private Integer timeoutHours;
    private Integer notifyIntervalHours;
    private LocalDateTime createTime;
}
