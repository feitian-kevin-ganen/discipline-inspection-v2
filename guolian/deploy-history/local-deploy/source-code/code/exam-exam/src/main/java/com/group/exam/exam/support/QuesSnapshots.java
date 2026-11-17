package com.group.exam.exam.support;

import com.group.exam.question.entity.QuesItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 题目快照构造：从题库题目复制完整内容（stem/options/answer/analysis/difficulty/quesType），
 * 封存后不受题库修改影响
 */
public final class QuesSnapshots {

    private QuesSnapshots() {
    }

    public static Map<String, Object> of(QuesItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("quesId", item.getId());
        snapshot.put("quesType", item.getQuesType());
        snapshot.put("stem", item.getStem());
        snapshot.put("options", item.getOptions());
        snapshot.put("answer", item.getAnswer());
        snapshot.put("analysis", item.getAnalysis());
        snapshot.put("difficulty", item.getDifficulty());
        return snapshot;
    }
}
