package com.group.exam.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 站内消息/通知（一接收人一行），表 t_sys_notice（无 deleted 字段）
 */
@TableName(value = "t_sys_notice", autoResultMap = true)
public class SysNotice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1考试通知 2成绩发布 3学习任务提醒 9系统公告 */
    private Integer noticeType;

    private String title;

    private String content;

    /** 关联业务：session/task 等 */
    private String bizType;

    private Long bizId;

    private Long receiverId;

    /** 0未读 1已读 */
    private Integer readStatus;

    private LocalDateTime readTime;

    /** 通道：1站内 2短信(预留) 3企业微信(预留) */
    private Integer channel;

    /** 发送状态：1成功 */
    private Integer sendStatus;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(Integer noticeType) {
        this.noticeType = noticeType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public Integer getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(Integer readStatus) {
        this.readStatus = readStatus;
    }

    public LocalDateTime getReadTime() {
        return readTime;
    }

    public void setReadTime(LocalDateTime readTime) {
        this.readTime = readTime;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public Integer getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(Integer sendStatus) {
        this.sendStatus = sendStatus;
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
}
