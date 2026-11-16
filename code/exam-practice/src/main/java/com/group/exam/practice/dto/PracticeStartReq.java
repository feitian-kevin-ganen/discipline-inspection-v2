package com.group.exam.practice.dto;

import java.io.Serializable;

/**
 * PRAC-01 发起练习请求
 */
public class PracticeStartReq implements Serializable {

    /** CATEGORY（按制度法规分类）/ RANDOM（全库随机），默认 RANDOM */
    private String mode;

    /** mode=CATEGORY 时必填，题库分类 ID（含子分类） */
    private Long bankId;

    /** 抽题数量，可选 5/10/20，默认取系统参数 practice.pageSize(10)，上限 practice.maxCount */
    private Integer count;

    /** BASIC / ADVANCED（映射 difficulty IN (2,3)），不传不限 */
    private String difficulty;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Long getBankId() {
        return bankId;
    }

    public void setBankId(Long bankId) {
        this.bankId = bankId;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
