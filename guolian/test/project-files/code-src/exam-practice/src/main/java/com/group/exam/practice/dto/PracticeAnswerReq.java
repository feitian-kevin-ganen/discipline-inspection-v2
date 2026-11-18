package com.group.exam.practice.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * PRAC-02 提交单题作答请求
 */
public class PracticeAnswerReq implements Serializable {

    /** 题目 ID（PRAC-01 返回） */
    private Long quesItemId;

    /** 单选 {"selected":["B"]}；填空 {"blanks":[...]}；简答 {"text":"..."} */
    private Map<String, Object> answer;

    /** 本次练习模式：CATEGORY / RANDOM（落练习记录） */
    private String mode;

    /** 分类练习时所选分类（落练习记录快照） */
    private Long bankId;

    /** 本题作答用时（秒） */
    private Integer durationSec;

    /** 客户端事件 UUID，幂等去重键（重复提交返回首次判分结果） */
    private String clientEventId;

    public Long getQuesItemId() {
        return quesItemId;
    }

    public void setQuesItemId(Long quesItemId) {
        this.quesItemId = quesItemId;
    }

    public Map<String, Object> getAnswer() {
        return answer;
    }

    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Long getBankId() {
        return bankId;
    }

    public void setBankId(Long bankId) {
        this.bankId = bankId;
    }

    public Integer getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public String getClientEventId() {
        return clientEventId;
    }

    public void setClientEventId(String clientEventId) {
        this.clientEventId = clientEventId;
    }
}
