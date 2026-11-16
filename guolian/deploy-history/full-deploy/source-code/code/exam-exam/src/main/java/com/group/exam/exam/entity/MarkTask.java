package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 阅卷任务（t_mark_task，无 deleted 字段）
 *
 * <p>round_no：1一评 2二评 3仲裁；mark_status：0待阅 1已阅 2仲裁中 3已仲裁。
 */
@Data
@TableName(value = "t_mark_task", autoResultMap = true)
public class MarkTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long sheetId;

    private Long detailId;

    private Long quesId;

    private Long markerId;

    private String markerName;

    /** 评阅轮次：1一评 */
    private Integer roundNo;

    /** 0待阅 1已阅 2仲裁中 3已仲裁 */
    private Integer markStatus;

    private BigDecimal score;

    private String comment;

    private LocalDateTime markTime;

    private Long assignBy;

    private LocalDateTime assignTime;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
