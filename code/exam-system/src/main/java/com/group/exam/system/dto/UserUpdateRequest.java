package com.group.exam.system.dto;


import java.util.List;

/**
 * USER-04 编辑人员请求（工号不可改；roleCodes 传则整体替换）
 */
public class UserUpdateRequest {

    private String name;

    private String phone;

    /** M/F */
    private String gender;

    private Long orgId;

    private String position;

    private List<String> roleCodes;

    /** 国联APP员工ID（ticket免密登录匹配用，可空） */
    private String empId;
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getEmpId() {
        return empId;
    }

    public void setEmpId(String empId) {
        this.empId = empId;
    }
}
