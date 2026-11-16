package com.group.exam.question.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 题目详情（QUES-07）：QUES-06 全部字段 + 审核/统计/时间字段
 */
public class ItemDetailVO implements Serializable {

    private Long itemId;

    private Long bankId;

    private String bankName;

    private String quesType;

    private String stem;

    /** API 结构：[{optKey,content,isAnswer}] */
    private List<OptionVO> options;

    /** API 结构答案：判断 {"value":"T"}；填空 {"blanks":[{no,answers,ordered}]}；简答 {"reference","keyPoints"}；选择 {"keys":[...]} */
    private Map<String, Object> answer;

    private String analysis;

    private String difficulty;

    private List<String> tags;

    private BigDecimal scoreSuggest;

    private String source;

    /** 来源制度法规名称(V1.1) */
    private String sourceLaw;

    /** 来源章节(V1.1) */
    private String sourceChapter;

    private Map<String, Object> fillConfig;

    private String status;

    private Integer useCount;

    private BigDecimal correctRate;

    private Long auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime auditTime;

    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;

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

    public String getStem() {
        return this.stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public List<OptionVO> getOptions() {
        return this.options;
    }

    public void setOptions(List<OptionVO> options) {
        this.options = options;
    }

    public Map<String, Object> getAnswer() {
        return this.answer;
    }

    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer;
    }

    public String getAnalysis() {
        return this.analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getDifficulty() {
        return this.difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public BigDecimal getScoreSuggest() {
        return this.scoreSuggest;
    }

    public void setScoreSuggest(BigDecimal scoreSuggest) {
        this.scoreSuggest = scoreSuggest;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceLaw() {
        return this.sourceLaw;
    }

    public void setSourceLaw(String sourceLaw) {
        this.sourceLaw = sourceLaw;
    }

    public String getSourceChapter() {
        return this.sourceChapter;
    }

    public void setSourceChapter(String sourceChapter) {
        this.sourceChapter = sourceChapter;
    }

    public Map<String, Object> getFillConfig() {
        return this.fillConfig;
    }

    public void setFillConfig(Map<String, Object> fillConfig) {
        this.fillConfig = fillConfig;
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

    public Long getAuditBy() {
        return this.auditBy;
    }

    public void setAuditBy(Long auditBy) {
        this.auditBy = auditBy;
    }

    public LocalDateTime getAuditTime() {
        return this.auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public Long getCreatedBy() {
        return this.createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedTime() {
        return this.createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return this.updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    /**
     * 选项（API 结构）
     */
    public static class OptionVO implements Serializable {

        private String optKey;

        private String content;

        private Boolean isAnswer;

        public String getOptKey() {
            return this.optKey;
        }

        public void setOptKey(String optKey) {
            this.optKey = optKey;
        }

        public String getContent() {
            return this.content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Boolean getIsAnswer() {
            return this.isAnswer;
        }

        public void setIsAnswer(Boolean isAnswer) {
            this.isAnswer = isAnswer;
        }
    }
}
