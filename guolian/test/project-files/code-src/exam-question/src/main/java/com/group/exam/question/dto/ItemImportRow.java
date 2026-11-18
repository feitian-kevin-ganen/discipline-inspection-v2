package com.group.exam.question.dto;

import com.alibaba.excel.annotation.ExcelProperty;

import java.io.Serializable;

/**
 * 题目导入 Excel 行模型（QUES-10 / QUES-11 模板列）。
 *
 * <p>答案列约定：选择题填选项字母（单选如 "A"，多选如 "ABC"）；判断题填 T/F（或对/错）；
 * 填空题多个空用 "||" 分隔，同一空的同义答案用 "|" 分隔；简答题直接填参考答案文本。
 */
public class ItemImportRow implements Serializable {

    @ExcelProperty(value = "题库分类", index = 0)
    private String bankName;

    @ExcelProperty(value = "题型", index = 1)
    private String quesType;

    @ExcelProperty(value = "题干", index = 2)
    private String stem;

    @ExcelProperty(value = "选项A", index = 3)
    private String optionA;

    @ExcelProperty(value = "选项B", index = 4)
    private String optionB;

    @ExcelProperty(value = "选项C", index = 5)
    private String optionC;

    @ExcelProperty(value = "选项D", index = 6)
    private String optionD;

    @ExcelProperty(value = "选项E", index = 7)
    private String optionE;

    @ExcelProperty(value = "选项F", index = 8)
    private String optionF;

    @ExcelProperty(value = "答案", index = 9)
    private String answer;

    @ExcelProperty(value = "解析", index = 10)
    private String analysis;

    @ExcelProperty(value = "难度", index = 11)
    private String difficulty;

    @ExcelProperty(value = "知识点标签", index = 12)
    private String tags;

    @ExcelProperty(value = "分值建议", index = 13)
    private String scoreSuggest;

    @ExcelProperty(value = "来源", index = 14)
    private String source;

    public String getBankName() {
        return this.bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getQuesType() {
        return this.quesType;
    }

    public void setQuesType(String quesType) {
        this.quesType = quesType;
    }

    public String getStem() {
        return this.stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public String getOptionA() {
        return this.optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return this.optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return this.optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return this.optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public String getOptionE() {
        return this.optionE;
    }

    public void setOptionE(String optionE) {
        this.optionE = optionE;
    }

    public String getOptionF() {
        return this.optionF;
    }

    public void setOptionF(String optionF) {
        this.optionF = optionF;
    }

    public String getAnswer() {
        return this.answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getAnalysis() {
        return this.analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getDifficulty() {
        return this.difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getTags() {
        return this.tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getScoreSuggest() {
        return this.scoreSuggest;
    }

    public void setScoreSuggest(String scoreSuggest) {
        this.scoreSuggest = scoreSuggest;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
