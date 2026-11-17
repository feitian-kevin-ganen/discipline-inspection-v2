package com.group.exam.question.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 题干相似度查重请求（QUES-12）
 */
public class DupCheckReq implements Serializable {

    @NotBlank(message = "stem 不能为空")
    private String stem;

    /** SINGLE/MULTI/JUDGE/FILL/ESSAY，可空表示不限题型 */
    private String quesType;

    /** 排除的题目 ID（编辑场景自查），可空 */
    private Long excludeItemId;

    public String getStem() {
        return this.stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public String getQuesType() {
        return this.quesType;
    }

    public void setQuesType(String quesType) {
        this.quesType = quesType;
    }

    public Long getExcludeItemId() {
        return this.excludeItemId;
    }

    public void setExcludeItemId(Long excludeItemId) {
        this.excludeItemId = excludeItemId;
    }
}
