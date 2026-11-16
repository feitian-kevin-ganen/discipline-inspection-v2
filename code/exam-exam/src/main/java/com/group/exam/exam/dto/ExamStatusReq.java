package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 场次状态流转（EXAM-06）：action START/END/CANCEL
 */
@Data
public class ExamStatusReq implements Serializable {

    private String action;
}
