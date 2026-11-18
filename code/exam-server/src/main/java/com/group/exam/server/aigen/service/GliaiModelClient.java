package com.group.exam.server.aigen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.group.exam.common.util.JsonUtils;
import com.group.exam.server.aigen.entity.AiGenQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 国联集团模型平台客户端（OpenAI 兼容协议），AI 生题真实模型实现。
 * 手册：https://glai2.wuxidata.com.cn:4001/doc/AI模型使用手册.html
 * 默认模型 deepseek-v4-flash（网关唯一 DeepSeek，官方推荐通用对话首选）。
 */
@Component
public class GliaiModelClient {

    private static final Logger log = LoggerFactory.getLogger(GliaiModelClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String ocrModel;
    private final int timeoutSeconds;
    private final int maxTokens;
    private final HttpClient http;

    public GliaiModelClient(@Value("${exam.ai-gen.base-url:https://glai2.wuxidata.com.cn:4001}") String baseUrl,
                            @Value("${exam.ai-gen.api-key:}") String apiKey,
                            @Value("${exam.ai-gen.model:deepseek-v4-flash}") String model,
                            @Value("${exam.ai-gen.ocr-model:PaddleOCR}") String ocrModel,
                            @Value("${exam.ai-gen.timeout-seconds:120}") int timeoutSeconds,
                            @Value("${exam.ai-gen.max-tokens:8192}") int maxTokens) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.ocrModel = ocrModel;
        this.timeoutSeconds = timeoutSeconds;
        this.maxTokens = maxTokens;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * PaddleOCR 识别单页图片（JPEG 字节），返回识别文本。
     * 扫描版 PDF 正文提取用（图片转 base64 走多模态 chat 接口）。
     */
    public String ocrImage(byte[] jpegBytes) {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("未配置 exam.ai-gen.api-key，无法调用 OCR 模型");
        }
        String b64 = java.util.Base64.getEncoder().encodeToString(jpegBytes);
        Map<String, Object> imageUrl = Map.of("url", "data:image/jpeg;base64," + b64);
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);
        Map<String, Object> textPart = Map.of("type", "text",
                "text", "识别图片中的所有文字，按原文顺序输出纯文本，不要添加任何说明或标点改动。");
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(imagePart, textPart));

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("model", ocrModel);
        reqBody.put("messages", List.of(message));
        reqBody.put("stream", false);
        String reqJson = JsonUtils.toJson(reqBody);

        log.info("OCR调用开始 model={} imageBytes={} 请求体={}", ocrModel, jpegBytes.length, reqJson);
        long startMs = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("OCR 网关返回 " + resp.statusCode() + ": " + abbreviate(resp.body(), 300));
            }
            JsonNode root = JsonUtils.fromJson(resp.body(), JsonNode.class);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            log.info("OCR调用完成 http={} 耗时={}ms 识别文本={}", resp.statusCode(), System.currentTimeMillis() - startMs, content);
            if (content.isBlank()) {
                throw new IllegalStateException("OCR 返回内容为空");
            }
            return content;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用 OCR 模型失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用大模型生成题目。
     *
     * @param materialTitle 资料标题
     * @param materialText  资料正文（图文类型可提取；文档/视频暂只给标题）
     * @param typeCounts    题型数量（API 编码 → 数量）
     * @param difficulties  难度分配序列
     * @param existingStems 已有题目题干（提示模型避免重复）
     */
    public List<AiGenQuestion> generate(String materialTitle, String materialText,
                                        Map<String, Integer> typeCounts,
                                        List<String> difficulties,
                                        List<String> existingStems) {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("未配置 exam.ai-gen.api-key，无法调用大模型");
        }
        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("model", model);
        reqBody.put("temperature", 0.6);
        reqBody.put("max_tokens", maxTokens);
        reqBody.put("stream", false);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        messages.add(Map.of("role", "user", "content", userPrompt(materialTitle, materialText, typeCounts, difficulties, existingStems)));
        reqBody.put("messages", messages);
        String reqJson = JsonUtils.toJson(reqBody);

        log.info("AI生题调用开始 model={} temperature=0.6 maxTokens={} 请求体={}", model, maxTokens, reqJson);
        long startMs = System.currentTimeMillis();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("模型网关返回 " + resp.statusCode() + ": " + abbreviate(resp.body(), 300));
            }
            JsonNode root = JsonUtils.fromJson(resp.body(), JsonNode.class);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            log.info("AI生题调用完成 http={} 耗时={}ms 模型原始输出={}", resp.statusCode(), System.currentTimeMillis() - startMs, content);
            return parseQuestions(content);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用大模型失败: " + e.getMessage(), e);
        }
    }

    private String systemPrompt() {
        return "你是纪检监察业务培训出题专家。根据用户给出的学习资料信息按要求生成试题。"
                + "严格只输出 JSON 数组，不要输出任何其他文字（不要 markdown 代码块）。"
                + "数组每个元素字段："
                + "quesType（SINGLE单选/MULTI多选/JUDGE判断/FILL填空/ESSAY简答）、"
                + "difficulty（EASY易/MEDIUM中/HARD难）、"
                + "stem（题干，表述严谨）、"
                + "options（仅单选/多选需要，格式 [{\"optKey\":\"A\",\"content\":\"选项内容\",\"isAnswer\":true}]，"
                + "共4个选项，isAnswer=true 表示正确答案；单选题恰好一个正确答案，多选题至少一个）、"
                + "answer（仅判断题与简答题需要：判断题 {\"value\":\"T\"} 或 {\"value\":\"F\"}；"
                + "简答题 {\"reference\":\"参考答案\",\"keyPoints\":[\"要点1\",\"要点2\"]}）、"
                + "analysis（答案解析，说明依据）。"
                + "题干与选项内容必须围绕给定资料，答案必须准确。";
    }

    private String userPrompt(String title, String text, Map<String, Integer> typeCounts,
                              List<String> difficulties, List<String> existingStems) {
        StringBuilder sb = new StringBuilder();
        sb.append("学习资料：《").append(title).append("》");
        if (text != null && !text.isBlank()) {
            String t = text.trim();
            sb.append("\n资料内容（节选）：\n").append(t.length() > 3000 ? t.substring(0, 3000) : t);
        }
        sb.append("\n\n请生成以下分布的题目：");
        typeCounts.forEach((k, v) -> {
            if (v != null && v > 0) {
                sb.append(typeName(k)).append(v).append("道；");
            }
        });
        long easy = difficulties.stream().filter("EASY"::equals).count();
        long medium = difficulties.stream().filter("MEDIUM"::equals).count();
        long hard = difficulties.stream().filter("HARD"::equals).count();
        sb.append("\n难度分布：易").append(easy).append("道、中").append(medium).append("道、难").append(hard).append("道。");
        if (existingStems != null && !existingStems.isEmpty()) {
            sb.append("\n以下题目已存在，请勿重复生成：");
            int limit = Math.min(existingStems.size(), 30);
            for (int i = 0; i < limit; i++) {
                String s = existingStems.get(i);
                sb.append("\n- ").append(s.length() > 80 ? s.substring(0, 80) : s);
            }
        }
        sb.append("\n只输出 JSON 数组。");
        return sb.toString();
    }

    /** 从模型输出中解析题目：容错提取 JSON 数组，逐项校验映射 */
    private List<AiGenQuestion> parseQuestions(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型返回内容为空");
        }
        String json = extractJsonArray(content);
        JsonNode arr;
        try {
            arr = JsonUtils.fromJson(json, JsonNode.class);
        } catch (Exception e) {
            throw new IllegalStateException("模型输出不是合法 JSON: " + abbreviate(content, 200), e);
        }
        if (!arr.isArray() || arr.isEmpty()) {
            throw new IllegalStateException("模型未返回题目数组");
        }
        List<AiGenQuestion> out = new ArrayList<>();
        int invalid = 0;
        for (JsonNode node : arr) {
            try {
                AiGenQuestion q = toQuestion(node);
                if (q != null) {
                    out.add(q);
                } else {
                    invalid++;
                }
            } catch (Exception e) {
                invalid++;
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("模型返回的题目均不符合格式要求");
        }
        if (invalid > 0) {
            log.warn("AI 生题解析：{} 道题目被丢弃（格式不合规）", invalid);
        }
        return out;
    }

    private AiGenQuestion toQuestion(JsonNode node) {
        String type = norm(node.path("quesType").asText(""));
        String difficulty = norm(node.path("difficulty").asText(""));
        String stem = node.path("stem").asText("").trim();
        Set<String> types = Set.of("SINGLE", "MULTI", "JUDGE", "FILL", "ESSAY");
        Set<String> diffs = Set.of("EASY", "MEDIUM", "HARD");
        if (!types.contains(type) || !diffs.contains(difficulty) || stem.isEmpty()) {
            return null;
        }
        AiGenQuestion q = new AiGenQuestion();
        q.setStatus("PENDING");
        q.setQuesType(type);
        q.setDifficulty(difficulty);
        q.setStem(stem);
        q.setAnalysis(node.path("analysis").asText(null));

        if ("SINGLE".equals(type) || "MULTI".equals(type)) {
            JsonNode opts = node.path("options");
            if (!opts.isArray() || opts.size() < 2) {
                return null;
            }
            List<Map<String, Object>> options = new ArrayList<>();
            boolean hasAnswer = false;
            for (JsonNode o : opts) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("optKey", o.path("optKey").asText("").trim());
                m.put("content", o.path("content").asText("").trim());
                m.put("isAnswer", o.path("isAnswer").asBoolean(false));
                if ((Boolean) m.get("isAnswer")) {
                    hasAnswer = true;
                }
                options.add(m);
            }
            if (!hasAnswer) {
                return null;
            }
            q.setOptionsJson(JsonUtils.toJson(options));
        } else if ("JUDGE".equals(type)) {
            String value = node.path("answer").path("value").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            if (!"T".equals(value) && !"F".equals(value)) {
                return null;
            }
            q.setAnswerJson("{\"value\":\"" + value + "\"}");
        } else if ("ESSAY".equals(type)) {
            JsonNode ans = node.path("answer");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reference", ans.path("reference").asText(""));
            List<String> points = new ArrayList<>();
            ans.path("keyPoints").forEach(p -> points.add(p.asText("")));
            m.put("keyPoints", points);
            q.setAnswerJson(JsonUtils.toJson(m));
        }
        return q;
    }

    /** 提取 JSON 数组文本：兼容 markdown 代码块与前后多余文字 */
    private String extractJsonArray(String content) {
        String s = content.trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        // 代码块包裹的情况
        s = s.replace("```json", "").replace("```", "").trim();
        start = s.indexOf('[');
        end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        throw new IllegalStateException("模型输出中未找到 JSON 数组");
    }

    private String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String typeName(String apiCode) {
        return switch (apiCode) {
            case "SINGLE" -> "单选";
            case "MULTI" -> "多选";
            case "JUDGE" -> "判断";
            case "FILL" -> "填空";
            default -> "简答";
        };
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
