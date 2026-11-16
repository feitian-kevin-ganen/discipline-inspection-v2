package com.group.exam.exam.dto;

import java.io.Serializable;
import java.util.List;

/**
 * NOTIFY-WX-03 上报订阅授权结果请求（wx.requestSubscribeMessage 回调）
 */
public class SubscribeReportReq implements Serializable {

    private List<Item> results;

    public List<Item> getResults() {
        return results;
    }

    public void setResults(List<Item> results) {
        this.results = results;
    }

    /**
     * 单场景授权结果：action=accept（订阅成功，额度+1）/ reject（拒绝）/ ban（拒绝且不再询问）
     */
    public static class Item implements Serializable {

        /** 场景编码：EXAM_PUBLISH/EXAM_REMIND_BEFORE/EXAM_REMIND_DEADLINE/SCORE_PUBLISHED */
        private String scene;

        /** 模板 ID（须与系统参数 wx.tpl.* 配置一致） */
        private String tplId;

        /** accept / reject / ban */
        private String action;

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public String getTplId() {
            return tplId;
        }

        public void setTplId(String tplId) {
            this.tplId = tplId;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }
    }
}
