package com.group.exam.server.aigen.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * AI 生成题目的人工编辑请求（采纳前可改题干/选项/答案/解析/难度）。
 */
public class QuestionUpdateReq implements Serializable {

    private String stem;

    /** 选择题选项：[{"optKey":"A","content":"...","isAnswer":true}] */
    private List<Map<String, Object>> options;

    /** 答案：判断 {"value":"T"}；简答 {"reference":"...","keyPoints":[...]} */
    private Map<String, Object> answer;

    private String analysis;

    private String difficulty;

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public List<Map<String, Object>> getOptions() {
        return options;
    }

    public void setOptions(List<Map<String, Object>> options) {
        this.options = options;
    }

    public Map<String, Object> getAnswer() {
        return answer;
    }

    public void setAnswer(Map<String, Object> answer) {
        this.answer = answer;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
