package com.group.exam.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * USER-02 新增人员请求
 */
public class UserCreateRequest {

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "工号不能为空")
    private String jobNo;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** M/F */
    private String gender;

    @NotNull(message = "所属单位不能为空")
    private Long orgId;

    private String position;

    @NotEmpty(message = "角色不能为空")
    private List<String> roleCodes;
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJobNo() {
        return jobNo;
    }

    public void setJobNo(String jobNo) {
        this.jobNo = jobNo;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes;
    }
}
