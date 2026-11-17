package com.group.exam.question.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 题目导入结果（QUES-10，结构同 USER-07）
 */
public class ImportResultVO implements Serializable {

    private int totalCount;

    private int successCount;

    private int failCount;

    private List<Failure> failures = new ArrayList<>();

    public void addFailure(int rowNo, String stemText, String reason) {
        Failure f = new Failure();
        f.setRowNo(rowNo);
        f.setStemText(stemText);
        f.setReason(reason);
        this.failures.add(f);
    }

    public int getTotalCount() {
        return this.totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getSuccessCount() {
        return this.successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return this.failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<Failure> getFailures() {
        return this.failures;
    }

    public void setFailures(List<Failure> failures) {
        this.failures = failures;
    }

    public static class Failure implements Serializable {

        /** Excel 行号（从 1 计，含表头） */
        private int rowNo;

        /** 题干摘要，便于定位 */
        private String stemText;

        private String reason;

        public int getRowNo() {
            return this.rowNo;
        }

        public void setRowNo(int rowNo) {
            this.rowNo = rowNo;
        }

        public String getStemText() {
            return this.stemText;
        }

        public void setStemText(String stemText) {
            this.stemText = stemText;
        }

        public String getReason() {
            return this.reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
