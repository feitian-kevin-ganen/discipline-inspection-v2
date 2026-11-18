package com.group.exam.server.aigen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 生成的单道题目（t_ai_gen_question）。
 * 状态：PENDING 待处理（可编辑/采纳/丢弃）/ ADOPTED 已采纳 / DISCARDED 已丢弃。
 * quesType/difficulty 存 API 编码（SINGLE/MULTI/JUDGE/FILL/ESSAY、EASY/MEDIUM/HARD），
 * options/answer 存 JSON，结构与 ItemSaveReq 一致，采纳时直接复用既有入库校验。
 */
@TableName("t_ai_gen_question")
public class AiGenQuestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** PENDING / ADOPTED / DISCARDED */
    private String status;

    private String quesType;

    private String difficulty;

    private String stem;

    /** 选择题选项 JSON：[{"optKey":"A","content":"...","isAnswer":true}]；其他题型为空 */
    private String optionsJson;

    /** 答案 JSON：判断 {"value":"T"}；简答 {"reference":"...","keyPoints":[...]}；选择/填空由 options/answer 组合生成 */
    private String answerJson;

    private String analysis;

    /** 采纳后对应的正式题目id */
    private Long adoptItemId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQuesType() {
        return quesType;
    }

    public void setQuesType(String quesType) {
        this.quesType = quesType;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public String getAnswerJson() {
        return answerJson;
    }

    public void setAnswerJson(String answerJson) {
        this.answerJson = answerJson;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public Long getAdoptItemId() {
        return adoptItemId;
    }

    public void setAdoptItemId(Long adoptItemId) {
        this.adoptItemId = adoptItemId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
