package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 逐题答案明细（t_answer_detail，无 deleted 字段）
 *
 * <p>is_right：1对 0错 2半对 NULL未判；mark_status：0未判 1自动已判 2人工已判 3待人工复核。
 */
@Data
@TableName(value = "t_answer_detail", autoResultMap = true)
public class AnswerDetail implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sheetId;

    private Long sessionId;

    private Long userId;

    private Long quesId;

    /** 1单选 2多选 3判断 4填空 5简答 */
    private Integer quesType;

    private Integer sortNo;

    /** 本题满分快照 */
    private BigDecimal quesScore;

    /** 选项乱序映射快照(V1.1)：{"A":"C",...} 展示key→原始key；填空/简答为NULL */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> optionOrder;

    /** 考生答案：选择{"keys":["A"]}；填空{"blanks":[...]}；简答{"text":"..."} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> userAnswer;

    /** 1对 0错 2半对 */
    private Integer isRight;

    private BigDecimal score;

    /** 0未判 1自动已判 2人工已判 3待人工复核 */
    private Integer markStatus;

    private LocalDateTime answerTime;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
