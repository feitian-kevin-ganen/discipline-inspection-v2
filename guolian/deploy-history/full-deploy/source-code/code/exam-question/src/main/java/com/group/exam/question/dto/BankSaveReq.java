package com.group.exam.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 题库分类保存请求（QUES-02 新增 / QUES-03 编辑；编辑时 parentId 忽略）
 */
public class BankSaveReq implements Serializable {

    /** 上级分类 ID，根节点传 0 */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    @NotBlank(message = "bankName 不能为空")
    @Size(max = 128, message = "bankName 长度不能超过 128")
    private String bankName;

    private Integer sortNo;

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
}
