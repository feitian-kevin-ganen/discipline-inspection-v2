package com.group.exam.exam.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 切屏/离开页面上报（TAKE-06）：eventType LEAVE/RETURN
 */
@Data
public class ScreenEventReq implements Serializable {

    private String eventType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime occurredAt;
}
