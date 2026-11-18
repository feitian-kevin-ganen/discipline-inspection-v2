package com.group.exam.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 题目保存请求（QUES-06 新增 / QUES-08 编辑，字段按 API 文档 4.7）
 */
public class ItemSaveReq implements Serializable {

    @NotNull(message = "bankId 不能为空")
    private Long bankId;

    /** SINGLE/MULTI/JUDGE/FILL/ESSAY */
    @NotBlank(message = "quesType 不能为空")
    private String quesType;

    /** 题干，富文本 HTML */
    @NotBlank(message = "stem 不能为空")
    private String stem;

    /** 选择题型必填：[{optKey,content,isAnswer}]；判断题可省略（固定 T/F） */
    @Valid
    private List<OptionReq> options;

    /**
     * 非选择题必填：判断 {"value":"T"}；
     * 填空 {"blanks":[{"no":1,"answers":["答案1","等价答案"],"ordered":false}]}；
     * 简答 {"reference":"...","keyPoints":[...]}
     */
    private Map<String, Object> answer;

    private String analysis;

    /** EASY/MEDIUM/HARD */
    @NotBlank(message = "difficulty 不能为空")
    private String difficulty;

    private List<String> tags;

    private BigDecimal scoreSuggest;

    private String source;

    /** 来源制度法规名称(V1.1)，如《中国共产党纪律处分条例》 */
    private String sourceLaw;

    /** 来源章节(V1.1) */
    private String sourceChapter;

    /** 题目来源：AI=AI生成采纳，MANUAL=人工录入（默认）；仅 create 生效，update 不改变来源 */
    private String genType;

    /** 填空判分配置 {ignoreCase,ignoreWidth,blankOrdered} */
    private Map<String, Object> fillConfig;

    public Long getBankId() {
        return this.bankId;
    }

    public void setBankId(Long bankId) {
        this.bankId = bankId;
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

    public List<OptionReq> getOptions() {
        return this.options;
    }

    public void setOptions(List<OptionReq> options) {
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

    public String getGenType() {
        return this.genType;
    }

    public void setGenType(String genType) {
        this.genType = genType;
    }

    public Map<String, Object> getFillConfig() {
        return this.fillConfig;
    }

    public void setFillConfig(Map<String, Object> fillConfig) {
        this.fillConfig = fillConfig;
    }

    /**
     * 选项（API 结构）：选择题 isAnswer 标记正确项
     */
    public static class OptionReq implements Serializable {

        @NotBlank(message = "选项 optKey 不能为空")
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
