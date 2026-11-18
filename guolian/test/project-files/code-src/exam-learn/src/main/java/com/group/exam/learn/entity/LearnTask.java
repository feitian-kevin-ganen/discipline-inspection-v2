package com.group.exam.learn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbLongListHandler;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学习任务，表 t_learn_task
 */
@Data
@TableName(value = "t_learn_task", autoResultMap = true)
public class LearnTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;

    /** 任务资料id数组 */
    @TableField(typeHandler = JsonbLongListHandler.class)
    private List<Long> materialIds;

    /** 要求有效学习时长(秒) */
    private Integer requiredDurationSec;

    private LocalDateTime deadline;

    /** 目标范围：{"orgPaths":[...],"withChildren":true,"userIds":[...]} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> targetScope;

    private Long publishOrgId;

    /** 下达单位物化路径快照，数据权限过滤依据 */
    private String publishOrgPath;

    /** 1进行中 2已结束 0已撤销 */
    private Integer status;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
