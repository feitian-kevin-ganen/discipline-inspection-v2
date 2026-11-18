package com.group.exam.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbListMapHandler;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbStringListHandler;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 题目（t_ques_item）
 *
 * <p>ques_type：1单选 2多选 3判断 4填空 5简答；difficulty：1易 2中 3难；
 * status：0草稿 1待审 2启用 3停用（仅启用可组卷）。
 * options JSONB：[{"key":"A","content":"..."}]；
 * answer JSONB：客观题{"keys":["A"]}；填空{"blanks":[["答1","同义"]],"config":{...}}；
 * 简答{"reference":"...","points":[...]}。
 */
@TableName(value = "t_ques_item", autoResultMap = true)
public class QuesItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bankId;

    /** 1单选 2多选 3判断 4填空 5简答 */
    private Integer quesType;

    /** 题干，富文本HTML */
    private String stem;

    /** 题干规范化MD5，查重辅助 */
    private String stemMd5;

    /** 选项JSONB：[{"key":"A","content":"..."}] */
    @TableField(typeHandler = JsonbListMapHandler.class)
    private List<Map<String, Object>> options;

    /** 答案JSONB，结构见类注释 */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> answer;

    /** 解析，富文本 */
    private String analysis;

    /** 1易 2中 3难 */
    private Integer difficulty;

    /** 分值建议 */
    private BigDecimal scoreSuggest;

    /** 知识点/分类标签 */
    @TableField(typeHandler = JsonbStringListHandler.class)
    private List<String> knowledgeTags;

    private String source;

    /** 来源制度法规名称(V1.1)，如《中国共产党纪律处分条例》 */
    private String sourceLaw;

    /** 来源章节(V1.1) */
    private String sourceChapter;

    /** 题目来源：AI=AI生成（采纳入库），MANUAL=人工录入/导入 */
    private String genType;

    /** 0草稿 1待审 2启用 3停用 */
    private Integer status;

    private Long auditBy;

    private LocalDateTime auditTime;

    /** 统计冗余字段，由判分模块回写，本模块不维护 */
    private Integer useCount;

    private Integer answerCount;

    private Integer rightCount;

    /** 历次考试正确率%（冗余） */
    private BigDecimal rightRate;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBankId() {
        return this.bankId;
    }

    public void setBankId(Long bankId) {
        this.bankId = bankId;
    }

    public Integer getQuesType() {
        return this.quesType;
    }

    public void setQuesType(Integer quesType) {
        this.quesType = quesType;
    }

    public String getStem() {
        return this.stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public String getStemMd5() {
        return this.stemMd5;
    }

    public void setStemMd5(String stemMd5) {
        this.stemMd5 = stemMd5;
    }

    public List<Map<String, Object>> getOptions() {
        return this.options;
    }

    public void setOptions(List<Map<String, Object>> options) {
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

    public Integer getDifficulty() {
        return this.difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public BigDecimal getScoreSuggest() {
        return this.scoreSuggest;
    }

    public void setScoreSuggest(BigDecimal scoreSuggest) {
        this.scoreSuggest = scoreSuggest;
    }

    public List<String> getKnowledgeTags() {
        return this.knowledgeTags;
    }

    public void setKnowledgeTags(List<String> knowledgeTags) {
        this.knowledgeTags = knowledgeTags;
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

    public Integer getStatus() {
        return this.status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public Integer getUseCount() {
        return this.useCount;
    }

    public void setUseCount(Integer useCount) {
        this.useCount = useCount;
    }

    public Integer getAnswerCount() {
        return this.answerCount;
    }

    public void setAnswerCount(Integer answerCount) {
        this.answerCount = answerCount;
    }

    public Integer getRightCount() {
        return this.rightCount;
    }

    public void setRightCount(Integer rightCount) {
        this.rightCount = rightCount;
    }

    public BigDecimal getRightRate() {
        return this.rightRate;
    }

    public void setRightRate(BigDecimal rightRate) {
        this.rightRate = rightRate;
    }

    public Long getCreateBy() {
        return this.createBy;
    }

    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return this.createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateBy() {
        return this.updateBy;
    }

    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return this.updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return this.deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
