package com.group.exam.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 纪检监察人员/系统账号，表 t_org_user
 */
@TableName(value = "t_org_user", autoResultMap = true)
public class OrgUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号（登录账号），全局唯一 */
    private String jobNo;

    private String userName;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    private String phone;

    /** 性别：1男(M) 2女(F) */
    private Integer gender;

    private Long orgId;

    /** 当前单位物化路径快照，数据权限过滤依据 */
    private String orgPath;

    private String position;

    /** 1正常 2锁定 3待修改初始密码 0停用 */
    private Integer accountStatus;

    private LocalDateTime pwdUpdateTime;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    /** 1手工录入 2Excel导入 3HR同步(预留) */
    private Integer source;

    /** 用户分组(V1.1)：1派驻组 2子企业纪委 3纪检委员 9其他纪检相关人员（默认） */
    private Integer userGroup;

    /** 微信小程序openid(V1.1)，微信登录绑定后写入，用于订阅消息推送寻址 */
    private String mpOpenid;

    /** 微信开放平台unionid(V1.1)，未绑定开放平台可为空 */
    private String unionid;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobNo() {
        return jobNo;
    }

    public void setJobNo(String jobNo) {
        this.jobNo = jobNo;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public String getOrgPath() {
        return orgPath;
    }

    public void setOrgPath(String orgPath) {
        this.orgPath = orgPath;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Integer getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(Integer accountStatus) {
        this.accountStatus = accountStatus;
    }

    public LocalDateTime getPwdUpdateTime() {
        return pwdUpdateTime;
    }

    public void setPwdUpdateTime(LocalDateTime pwdUpdateTime) {
        this.pwdUpdateTime = pwdUpdateTime;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public Integer getSource() {
        return source;
    }

    public void setSource(Integer source) {
        this.source = source;
    }

    public Integer getUserGroup() {
        return userGroup;
    }

    public void setUserGroup(Integer userGroup) {
        this.userGroup = userGroup;
    }

    public String getMpOpenid() {
        return mpOpenid;
    }

    public void setMpOpenid(String mpOpenid) {
        this.mpOpenid = mpOpenid;
    }

    public String getUnionid() {
        return unionid;
    }

    public void setUnionid(String unionid) {
        this.unionid = unionid;
    }

    public Long getCreateBy() {
        return createBy;
    }

    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
