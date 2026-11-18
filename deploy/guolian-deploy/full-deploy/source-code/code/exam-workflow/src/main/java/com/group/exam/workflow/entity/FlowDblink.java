package com.group.exam.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 流程节点外部数据源（仅登记与连测，不做任意 SQL 执行） */
@Data
@TableName("flow_dblink")
public class FlowDblink implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String dbType;
    private String jdbcUrl;
    private String username;
    /** 只接收前端传入，接口响应不返回明文密码 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    /** 白名单表，逗号分隔 */
    private String allowedTables;
    /** 0=启用 1=停用 */
    private String status;
    private LocalDateTime createTime;
}
