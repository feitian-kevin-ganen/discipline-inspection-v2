package com.group.exam.server.aigen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 生题任务（t_ai_gen_task）：一个学习资料一个任务行。
 * 状态：RUNNING 生成中 / SUCCESS 生成成功 / FAILED 生成失败。
 */
@TableName("t_ai_gen_task")
public class AiGenTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学习资料id */
    private Long materialId;

    /** 学习资料标题（冗余展示） */
    private String materialTitle;

    /** 采纳入库的目标题库（任务创建时按资料自动建立/复用） */
    private Long bankId;

    /** 生成题目总数 */
    private Integer quesCount;

    /** 题目明细汇总（题型:数量;难度:数量），任务成功后回写 */
    private String typeSummary;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long createBy;

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

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public String getMaterialTitle() {
        return materialTitle;
    }

    public void setMaterialTitle(String materialTitle) {
        this.materialTitle = materialTitle;
    }

    public Long getBankId() {
        return bankId;
    }

    public void setBankId(Long bankId) {
        this.bankId = bankId;
    }

    public Integer getQuesCount() {
        return quesCount;
    }

    public void setQuesCount(Integer quesCount) {
        this.quesCount = quesCount;
    }

    public String getTypeSummary() {
        return typeSummary;
    }

    public void setTypeSummary(String typeSummary) {
        this.typeSummary = typeSummary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getCreateBy() {
        return createBy;
    }

    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
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
