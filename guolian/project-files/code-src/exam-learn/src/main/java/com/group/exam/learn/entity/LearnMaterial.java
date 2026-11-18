package com.group.exam.learn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbStringListHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学习资料，表 t_learn_material
 */
@Data
@TableName(value = "t_learn_material", autoResultMap = true)
public class LearnMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String title;

    private String summary;

    /** 封面图对象名 */
    private String coverUrl;

    /** 1PDF 2Word 3PPT 4MP4视频 5图文 */
    private Integer fileType;

    /** 文件对象名（MinIO），图文类型为空 */
    private String fileUrl;

    /** 原始上传文件名（展示用），历史数据可能为空 */
    private String fileName;

    /** 图文正文（file_type=5） */
    private String content;

    private Long fileSize;

    private Integer pageCount;

    /** 标称时长(秒)，完成判定阈值依据 */
    private Integer durationSec;

    /** 标签数组 */
    @TableField(typeHandler = JsonbStringListHandler.class)
    private List<String> tags;

    /** 列表排序序号，小值在前 */
    private Integer sortNo;

    private Integer applyLevel;

    /** 可见范围：{"type":"ALL"} | {"type":"ORG","orgPaths":[...],"withChildren":true} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> visibleScope;

    /** 是否需要审核：1是 0否 */
    private Integer needAudit;

    /** 0草稿 1待审 2已发布 3已下架 4驳回 */
    private Integer auditStatus;

    private Long auditBy;

    private LocalDateTime auditTime;

    private Integer version;

    private Integer studyCount;

    private Long uploadOrgId;

    /** 上传单位物化路径快照，数据权限过滤依据 */
    private String uploadOrgPath;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
