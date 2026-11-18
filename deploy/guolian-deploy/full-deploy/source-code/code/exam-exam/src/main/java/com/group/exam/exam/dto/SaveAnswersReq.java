package com.group.exam.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 批量/增量保存答案（TAKE-05）
 */
@Data
public class SaveAnswersReq implements Serializable {

    /** 客户端批次 UUID，Redis 短期去重 */
    private String clientBatchId;

    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem implements Serializable {
        /** 答卷题目快照 ID（t_answer_detail 主键） */
        private Long sheetQuesId;
        /** {selected:["A"]} / {blanks:[...]} / {text:"..."} */
        private Map<String, Object> answer;
    }
}
