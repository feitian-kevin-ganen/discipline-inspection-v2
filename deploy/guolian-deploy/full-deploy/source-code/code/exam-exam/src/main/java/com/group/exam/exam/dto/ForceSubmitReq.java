package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 强制收卷（EXAM-13）：userIds 为空表示全部在考考生
 */
@Data
public class ForceSubmitReq implements Serializable {

    private List<Long> userIds;

    private String reason;
}
