package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 固定卷选题全量替换（PAPER-06）
 */
@Data
public class PaperQuestionsReq implements Serializable {

    private List<PaperSaveReq.QuestionItem> questions;
}
