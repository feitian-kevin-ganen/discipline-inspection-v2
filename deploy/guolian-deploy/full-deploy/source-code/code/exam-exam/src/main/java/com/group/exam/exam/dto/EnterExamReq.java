package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 进入考试（TAKE-02）
 */
@Data
public class EnterExamReq implements Serializable {

    private Boolean noticeConfirmed;

    private String deviceId;

    private String terminal;
}
