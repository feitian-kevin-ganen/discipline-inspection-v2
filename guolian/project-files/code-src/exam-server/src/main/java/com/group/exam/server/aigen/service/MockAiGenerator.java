package com.group.exam.server.aigen.service;

import com.group.exam.common.util.JsonUtils;
import com.group.exam.server.aigen.entity.AiGenQuestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型生题客户端（当前为 MOCK 实现）。
 * 后续接入真实模型时，仅替换本类的 generate 方法：
 * 输入资料内容 + 题型/难度分布，输出同结构的题目列表即可，任务编排/入库逻辑不变。
 * 模拟延迟在 AiGenService 中控制。
 */
public final class MockAiGenerator {

    private MockAiGenerator() {
    }

    /**
     * 生成 mock 题目。
     *
     * @param materialTitle 资料标题（mock 题面围绕资料标题展开）
     * @param typeCounts    题型数量（API 编码 → 数量）
     * @param difficulties  难度序列（按题目顺序分配，长度与题目总数一致）
     */
    public static List<AiGenQuestion> generate(String materialTitle,
                                               Map<String, Integer> typeCounts,
                                               List<String> difficulties) {
        String title = materialTitle == null || materialTitle.isBlank() ? "相关学习资料" : materialTitle.trim();
        List<AiGenQuestion> out = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<String, Integer> e : typeCounts.entrySet()) {
            String type = e.getKey();
            int n = e.getValue() == null ? 0 : Math.max(0, e.getValue());
            for (int i = 1; i <= n; i++) {
                AiGenQuestion q = new AiGenQuestion();
                q.setStatus("PENDING");
                q.setQuesType(type);
                q.setDifficulty(idx < difficulties.size() ? difficulties.get(idx) : "MEDIUM");
                q.setStem(buildStem(title, type, i));
                fillByType(q, title, type, i);
                q.setAnalysis("【mock 解析】依据《 " + title + " 》相关内容整理，接入真实模型后将基于资料原文生成解析。");
                out.add(q);
                idx++;
            }
        }
        return out;
    }

    private static String buildStem(String title, String type, int i) {
        return switch (type) {
            case "SINGLE" -> "【AI 示例】" + i + ". 根据《" + title + "》，下列关于该资料核心要求的表述，正确的是哪一项？";
            case "MULTI" -> "【AI 示例】" + i + ". 根据《" + title + "》，下列说法中正确的有哪些？";
            case "JUDGE" -> "【AI 示例】" + i + ". 《" + title + "》的相关要求与本题表述一致。（判断对错）";
            case "FILL" -> "【AI 示例】" + i + ". 根据《" + title + "》，资料强调的关键要求可概括为“______”。（填空）";
            default -> "【AI 示例】" + i + ". 请结合《" + title + "》，简述该资料的主要要求及落实要点。";
        };
    }

    private static void fillByType(AiGenQuestion q, String title, String type, int i) {
        switch (type) {
            case "SINGLE", "MULTI" -> {
                List<Map<String, Object>> opts = new ArrayList<>();
                String[] keys = {"A", "B", "C", "D"};
                String[] contents = {
                        "应当严格按资料要求执行并留存记录",
                        "可以在必要时变通执行，无需记录",
                        "仅适用于部分单位，不具有普遍性",
                        "与资料要求无关的表述"
                };
                for (int k = 0; k < keys.length; k++) {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("optKey", keys[k]);
                    o.put("content", contents[k]);
                    boolean ans = "SINGLE".equals(type) ? k == 0 : (k == 0 || k == 2);
                    o.put("isAnswer", ans);
                    opts.add(o);
                }
                q.setOptionsJson(JsonUtils.toJson(opts));
                q.setAnswerJson(null);
            }
            case "JUDGE" -> {
                Map<String, Object> ans = new LinkedHashMap<>();
                ans.put("value", i % 2 == 0 ? "F" : "T");
                q.setOptionsJson(null);
                q.setAnswerJson(JsonUtils.toJson(ans));
            }
            case "FILL" -> {
                q.setOptionsJson(null);
                q.setAnswerJson(null);
            }
            default -> {
                Map<String, Object> ans = new LinkedHashMap<>();
                ans.put("reference", "围绕《" + title + "》的核心要求作答，答出主要要点即可。");
                ans.put("keyPoints", List.of("要点一：理解资料适用范围", "要点二：掌握关键要求", "要点三：明确落实方式"));
                q.setOptionsJson(null);
                q.setAnswerJson(JsonUtils.toJson(ans));
            }
        }
    }
}
