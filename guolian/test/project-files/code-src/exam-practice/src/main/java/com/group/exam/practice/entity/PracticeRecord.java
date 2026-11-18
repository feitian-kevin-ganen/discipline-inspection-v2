package com.group.exam.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 练习记录（t_practice_record，V1.1 新增，一题一行、只增不改，无 update_by/update_time/deleted 字段）
 *
 * <p>practice_mode：1分类练习(CATEGORY) 2随机抽题(RANDOM)；is_right：1对 0错（即时判定，无半对）；
 * 组织与来源法规字段为作答时快照，调岗/题目调整不回刷。
 */
@Data
@TableName(value = "t_practice_record", autoResultMap = true)
public class PracticeRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 练习人姓名快照 */
    private String userName;

    private Long orgId;

    /** 当时单位物化路径快照 */
    private String orgPath;

    /** 当时单位名称快照 */
    private String orgName;

    private Long quesItemId;

    /** 题型快照：1单选 2多选 3判断 4填空 5简答 */
    private Integer quesType;

    /** 题库分类快照（分类练习所选分类） */
    private Long bankId;

    /** 来源制度法规快照 */
    private String sourceLaw;

    /** 来源章节快照 */
    private String sourceChapter;

    /** 练习模式：1分类练习(CATEGORY) 2随机抽题(RANDOM) */
    private Integer practiceMode;

    /** 用户答案JSONB：选择{"keys":["A"]}；判断{"keys":["T"]}；填空{"blanks":[...]} */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> userAnswer;

    /** 1对 0错（简答不支持自动判定时记 0，响应 isRight=null） */
    private Integer isRight;

    /** 本题作答用时（秒） */
    private Integer durationSec;

    private Long createBy;

    private LocalDateTime createTime;
}
