package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 推送考试通知（EXAM-11）
 */
@Data
public class ExamNotifyReq implements Serializable {

    private String message;

    /** 目前仅支持 INBOX，SMS/WECOM 预留忽略 */
    private List<String> channels;

    /** 指定接收考生（须已在考生名单内）；为空则推送给名单内全部考生 */
    private List<Long> userIds;
}
