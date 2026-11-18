package com.group.exam.system.dto;

import com.alibaba.excel.annotation.ExcelProperty;

/**
 * USER-07 人员导入 Excel 行模型（模板含两行表头：字段名 + 填写说明）
 */
public class UserImportRow {

    @ExcelProperty(index = 0)
    private String name;

    @ExcelProperty(index = 1)
    private String jobNo;

    @ExcelProperty(index = 2)
    private String phone;

    @ExcelProperty(index = 3)
    private String gender;

    @ExcelProperty(index = 4)
    private String orgCode;

    @ExcelProperty(index = 5)
    private String position;

    @ExcelProperty(index = 6)
    private String roleCodes;
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

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(String roleCodes) {
        this.roleCodes = roleCodes;
    }
}
