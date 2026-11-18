package com.group.exam.server.aigen.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * AI 生题任务创建请求：选定若干学习资料 + 题型数量 + 难度数量，
 * 每个资料创建一个异步生成任务。
 */
public class TaskCreateReq implements Serializable {

    /** 学习资料id列表（至少1个） */
    private List<Long> materialIds;

    /** 题型数量：{"SINGLE":5,"MULTI":3,"JUDGE":2,"FILL":0,"ESSAY":0}，总量即生成题目数 */
    private Map<String, Integer> typeCounts;

    /** 难度数量：{"EASY":5,"MEDIUM":3,"HARD":2}；总量与题型总量不一致时按顺序分配、不足补 MEDIUM */
    private Map<String, Integer> difficultyCounts;

    public List<Long> getMaterialIds() {
        return materialIds;
    }

    public void setMaterialIds(List<Long> materialIds) {
        this.materialIds = materialIds;
    }

    public Map<String, Integer> getTypeCounts() {
        return typeCounts;
    }

    public void setTypeCounts(Map<String, Integer> typeCounts) {
        this.typeCounts = typeCounts;
    }

    public Map<String, Integer> getDifficultyCounts() {
        return difficultyCounts;
    }

    public void setDifficultyCounts(Map<String, Integer> difficultyCounts) {
        this.difficultyCounts = difficultyCounts;
    }
}
