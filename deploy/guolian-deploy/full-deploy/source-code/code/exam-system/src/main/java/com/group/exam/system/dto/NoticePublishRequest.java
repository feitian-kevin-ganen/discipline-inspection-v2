package com.group.exam.system.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * SYS-07 发布通知消息请求
 */
public class NoticePublishRequest {

    /** EXAM 考试通知 / SCORE 成绩发布 / LEARN_TASK 学习任务 / GENERAL 通用 */
    @NotBlank(message = "noticeType 不能为空")
    private String noticeType;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;

    /** 目标单位（含下级展开） */
    private List<Long> targetOrgIds;

    /** 指定接收人 */
    private List<Long> targetUserIds;

    /** 是否含下级单位，默认 true */
    private Boolean withChildren;

    /** 全员发送 */
    private Boolean sendAll;

    /** 渠道：INBOX 站内（SMS/WECOM 预留） */
    private List<String> channels;
    public String getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(String noticeType) {
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

    public List<Long> getTargetOrgIds() {
        return targetOrgIds;
    }

    public void setTargetOrgIds(List<Long> targetOrgIds) {
        this.targetOrgIds = targetOrgIds;
    }

    public List<Long> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<Long> targetUserIds) {
        this.targetUserIds = targetUserIds;
    }

    public Boolean getWithChildren() {
        return withChildren;
    }

    public void setWithChildren(Boolean withChildren) {
        this.withChildren = withChildren;
    }

    public Boolean getSendAll() {
        return sendAll;
    }

    public void setSendAll(Boolean sendAll) {
        this.sendAll = sendAll;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }
}
