package com.group.exam.learn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学习记录（一人一份资料一条），表 t_learn_record
 */
@Data
@TableName("t_learn_record")
public class LearnRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String userName;

    private Long orgId;

    /** 学习发生时单位物化路径快照，调岗不回刷 */
    private String orgPath;

    private String orgName;

    private Long materialId;

    private String materialTitle;

    private Long categoryId;

    /** 学习进度百分比 0~100 */
    private BigDecimal progress;

    /** 文档最后阅读页码 */
    private Integer readPage;

    /** 视频最后播放位置(秒) */
    private Integer playPositionSec;

    /** 累计有效学习时长(秒) */
    private Integer effectiveDurationSec;

    private LocalDateTime lastHeartbeatTime;

    /** 0未开始 1进行中 2已完成 */
    private Integer finishStatus;

    private LocalDateTime finishTime;

    private LocalDateTime firstLearnTime;

    private LocalDateTime lastLearnTime;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
