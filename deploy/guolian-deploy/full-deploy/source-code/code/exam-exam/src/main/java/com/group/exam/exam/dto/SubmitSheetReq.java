package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 交卷（TAKE-07，幂等）
 */
@Data
public class SubmitSheetReq implements Serializable {

    private Boolean confirmUnanswered;

    private String clientSubmitId;
}
