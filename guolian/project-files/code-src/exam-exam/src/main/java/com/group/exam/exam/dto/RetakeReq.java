package com.group.exam.exam.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 补考设置（EXAM-14）：userIds 为空时取正考缺考/不及格人员
 */
@Data
public class RetakeReq implements Serializable {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime retakeStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime retakeEndTime;

    private List<Long> userIds;
}
