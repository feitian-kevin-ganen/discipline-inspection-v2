package com.group.exam.question.dto;


import java.io.Serializable;
import java.util.List;

/**
 * 题库分类树节点（QUES-01）
 */
public class BankTreeVO implements Serializable {

    private Long bankId;

    private Long parentId;

    private String bankName;

    private Integer sortNo;

    /** 本分类下未删除题目数（不含下级） */
    private Long itemCount;

    private List<BankTreeVO> children;

    public Long getBankId() {
        return this.bankId;
    }

    public void setBankId(Long bankId) {
        this.bankId = bankId;
    }

    public Long getParentId() {
        return this.parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getBankName() {
        return this.bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public Integer getSortNo() {
        return this.sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Long getItemCount() {
        return this.itemCount;
    }

    public void setItemCount(Long itemCount) {
        this.itemCount = itemCount;
    }

    public List<BankTreeVO> getChildren() {
        return this.children;
    }

    public void setChildren(List<BankTreeVO> children) {
        this.children = children;
    }
}
