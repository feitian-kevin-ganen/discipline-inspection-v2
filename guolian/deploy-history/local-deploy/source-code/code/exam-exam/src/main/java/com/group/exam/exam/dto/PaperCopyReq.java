package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 复制试卷（PAPER-08）
 */
@Data
public class PaperCopyReq implements Serializable {

    private String paperName;
}
