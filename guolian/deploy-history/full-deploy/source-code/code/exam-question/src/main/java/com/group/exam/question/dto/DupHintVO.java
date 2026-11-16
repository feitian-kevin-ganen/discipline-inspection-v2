package com.group.exam.question.dto;


import java.io.Serializable;

/**
 * 疑似重复题目提示（QUES-12 响应项 / QUES-06 dupHints 项）
 */
public class DupHintVO implements Serializable {

    private Long itemId;

    /** 相似度 [0,1]，精确命中为 1.0 */
    private Double similarity;

    /** 相似题干摘要（纯文本） */
    private String stemText;

    /** DRAFT/PENDING_AUDIT/ENABLED/DISABLED */
    private String status;

    public Long getItemId() {
        return this.itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Double getSimilarity() {
        return this.similarity;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }

    public String getStemText() {
        return this.stemText;
    }

    public void setStemText(String stemText) {
        this.stemText = stemText;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
