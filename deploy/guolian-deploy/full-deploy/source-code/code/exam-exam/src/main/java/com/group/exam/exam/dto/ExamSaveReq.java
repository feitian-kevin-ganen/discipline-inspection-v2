package com.group.exam.exam.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建/编辑测试场次（EXAM-02/04）
 */
@Data
public class ExamSaveReq implements Serializable {

    private String examName;

    private Long paperId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer durationMin;

    /** 允许迟到进入时限（分钟，相对 startTime），0 不允许迟到 */
    private Integer lateEnterMin;

    private Integer switchScreenLimit;

    private Boolean allowEarlySubmit;

    private Integer earlySubmitAfterMin;

    private Boolean shuffleQuestion;

    private Boolean shuffleOption;

    /** 测试须知富文本（t_exam_session 无独立列，存 paper_strategy_snapshot.notice） */
    private String notice;
}
