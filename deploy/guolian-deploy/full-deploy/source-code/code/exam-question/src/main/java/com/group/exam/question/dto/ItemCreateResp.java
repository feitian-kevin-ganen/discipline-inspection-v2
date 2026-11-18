package com.group.exam.question.dto;


import java.io.Serializable;
import java.util.List;

/**
 * 新增题目响应（QUES-06）
 */
public class ItemCreateResp implements Serializable {

    private Long itemId;

    /** DRAFT/PENDING_AUDIT/ENABLED/DISABLED */
    private String status;

    /** 保存前自动查重的疑似重复提示（不阻断） */
    private List<DupHintVO> dupHints;

    public Long getItemId() {
        return this.itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<DupHintVO> getDupHints() {
        return this.dupHints;
    }

    public void setDupHints(List<DupHintVO> dupHints) {
        this.dupHints = dupHints;
    }
}
