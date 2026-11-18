package com.group.exam.learn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 学习资料分类树，表 t_learn_category
 */
@Data
@TableName(value = "t_learn_category", autoResultMap = true)
public class LearnCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级分类id，根为 NULL/0 */
    private Long parentId;

    private String categoryName;

    private Integer level;

    private Integer sortNo;

    /** 可见组织范围：{"type":"ALL"} | {"type":"ORG","orgPaths":[...],"withChildren":true} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> visibleScope;

    /** 1启用 0停用 */
    private Integer status;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
