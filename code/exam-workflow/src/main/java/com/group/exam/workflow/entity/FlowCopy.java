package com.group.exam.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 流程抄送记录 */
@Data
@TableName("flow_copy")
public class FlowCopy implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String procInstId;
    private String taskDefKey;
    private String taskName;
    private String title;
    private Long fromUserId;
    private Long toUserId;
    /** 0=未读 1=已读 */
    private String readFlag;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
}
