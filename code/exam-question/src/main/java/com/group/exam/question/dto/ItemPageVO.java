package com.group.exam.question.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目分页列表项（QUES-05）
 */
public class ItemPageVO implements Serializable {

    private Long itemId;

    private Long bankId;

    private String bankName;

    /** SINGLE/MULTI/JUDGE/FILL/ESSAY */
    private String quesType;

    /** 题干纯文本摘要 */
    private String stemText;

    /** EASY/MEDIUM/HARD */
    private String difficulty;

    private BigDecimal scoreSuggest;

    private List<String> tags;

    /** DRAFT/PENDING_AUDIT/ENABLED/DISABLED */
    private String status;

    private Integer useCount;

    /** 历次考试正确率% */
    private BigDecimal correctRate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    public Long getItemId() {
        return this.itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getBankId() {
        return this.bankId;
    }

    public void setBankId(Long bankId) {
        this.bankId = bankId;
    }

    public String getBankName() {
        return this.bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getQuesType() {
        return this.quesType;
    }

    public void setQuesType(String quesType) {
        this.quesType = quesType;
    }

    public String getStemText() {
        return this.stemText;
    }

    public void setStemText(String stemText) {
        this.stemText = stemText;
    }

    public String getDifficulty() {
        return this.difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public BigDecimal getScoreSuggest() {
        return this.scoreSuggest;
    }

    public void setScoreSuggest(BigDecimal scoreSuggest) {
        this.scoreSuggest = scoreSuggest;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getUseCount() {
        return this.useCount;
    }

    public void setUseCount(Integer useCount) {
        this.useCount = useCount;
    }

    public BigDecimal getCorrectRate() {
        return this.correctRate;
    }

    public void setCorrectRate(BigDecimal correctRate) {
        this.correctRate = correctRate;
    }

    public LocalDateTime getCreatedTime() {
        return this.createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
