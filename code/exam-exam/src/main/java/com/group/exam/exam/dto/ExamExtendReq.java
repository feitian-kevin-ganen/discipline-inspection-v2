package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 延长测试时间（EXAM-12）
 */
@Data
public class ExamExtendReq implements Serializable {

    private Integer extendMin;
}
