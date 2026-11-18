package com.group.exam.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ORG-02 新增组织单位请求
 */
public class OrgCreateRequest {

    /** 上级单位ID；为空或 0 表示根级单位 */
    private Long parentId;

    @NotBlank(message = "单位名称不能为空")
    @Size(max = 50, message = "单位名称不超过50字")
    private String orgName;

    @NotBlank(message = "单位编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$", message = "单位编码须为字母数字")
    private String orgCode;

    /** HQ/L2/SUB/DEPT */
    @NotBlank(message = "单位类型不能为空")
    private String orgType;

    private String leaderName;

    private String phone;

    private Integer sortNo;
    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getOrgType() {
        return orgType;
    }

    public void setOrgType(String orgType) {
        this.orgType = orgType;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public void setLeaderName(String leaderName) {
        this.leaderName = leaderName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
