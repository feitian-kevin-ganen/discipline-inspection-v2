package com.group.exam.question.support;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.question.dto.ItemDetailVO;
import com.group.exam.question.dto.ItemSaveReq;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.DifficultyEnum;
import com.group.exam.question.enums.ItemStatusEnum;
import com.group.exam.question.enums.QuesTypeEnum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 题目 API 结构 ↔ 数据库存储结构转换（JSONB options/answer 口径见 t_ques_item 注释）。
 */
public final class QuesItemAssembler {

    /** 判断题固定选项 */
    public static final List<Map<String, Object>> JUDGE_OPTIONS = List.of(
            Map.of("key", "T", "content", "正确"),
            Map.of("key", "F", "content", "错误"));

    private QuesItemAssembler() {
    }

    /**
     * 将请求字段校验并填充到实体（quesType/difficulty/options/answer/stemMd5）。
     * 校验失败抛 10001。
     */
    public static void applyStorage(ItemSaveReq req, QuesItem item) {
        QuesTypeEnum type = QuesTypeEnum.parse(req.getQuesType());
        item.setQuesType(type.getCode());
        item.setDifficulty(DifficultyEnum.parse(req.getDifficulty()).getCode());
        item.setStemMd5(StemTextUtils.normalizeMd5(req.getStem()));

        switch (type) {
            case SINGLE, MULTI -> applyChoice(req, item, type);
            case JUDGE -> applyJudge(req, item);
            case FILL -> applyFill(req, item);
            case ESSAY -> applyEssay(req, item);
        }
    }

    private static void applyChoice(ItemSaveReq req, QuesItem item, QuesTypeEnum type) {
        List<ItemSaveReq.OptionReq> options = req.getOptions();
        if (options == null || options.size() < 2) {
            throw new BizException(ErrorCode.PARAM_ERROR, "选择题型至少需要 2 个选项");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> storedOptions = new ArrayList<>();
        List<String> answerKeys = new ArrayList<>();
        for (ItemSaveReq.OptionReq opt : options) {
            String key = opt.getOptKey() == null ? "" : opt.getOptKey().trim();
            if (key.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "选项 optKey 不能为空");
            }
            if (!seen.add(key)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "选项 optKey 重复: " + key);
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("key", key);
            o.put("content", opt.getContent() == null ? "" : opt.getContent());
            storedOptions.add(o);
            if (Boolean.TRUE.equals(opt.getIsAnswer())) {
                answerKeys.add(key);
            }
        }
        if (type == QuesTypeEnum.SINGLE && answerKeys.size() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单选题须且仅须 1 个正确选项");
        }
        if (type == QuesTypeEnum.MULTI && answerKeys.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "多选题至少 1 个正确选项");
        }
        item.setOptions(storedOptions);
        item.setAnswer(Map.of("keys", answerKeys));
    }

    private static void applyJudge(ItemSaveReq req, QuesItem item) {
        List<Map<String, Object>> judgeOptions = new ArrayList<>();
        for (Map<String, Object> o : JUDGE_OPTIONS) {
            judgeOptions.add(new LinkedHashMap<>(o));
        }
        item.setOptions(judgeOptions);
        String value = req.getAnswer() == null ? null : strVal(req.getAnswer().get("value"));
        if (value != null) {
            value = value.trim().toUpperCase(java.util.Locale.ROOT);
            if ("对".equals(value) || "正确".equals(value)) {
                value = "T";
            } else if ("错".equals(value) || "错误".equals(value)) {
                value = "F";
            }
        }
        if (!"T".equals(value) && !"F".equals(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "判断题答案须为 {\"value\":\"T\"} 或 {\"value\":\"F\"}");
        }
        item.setAnswer(Map.of("keys", List.of(value)));
    }

    @SuppressWarnings("unchecked")
    private static void applyFill(ItemSaveReq req, QuesItem item) {
        Object blanksObj = req.getAnswer() == null ? null : req.getAnswer().get("blanks");
        if (!(blanksObj instanceof List<?> blanks) || blanks.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "填空题答案须配置 blanks，至少 1 个空");
        }
        List<List<String>> storedBlanks = new ArrayList<>();
        for (Object b : blanks) {
            if (!(b instanceof Map<?, ?> blankMap)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "填空题 blanks 元素格式错误");
            }
            Object answersObj = ((Map<String, Object>) blankMap).get("answers");
            if (!(answersObj instanceof List<?> answers) || answers.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "填空题每个空至少 1 个答案");
            }
            List<String> ans = new ArrayList<>();
            for (Object a : answers) {
                String s = a == null ? "" : a.toString().trim();
                if (!s.isEmpty()) {
                    ans.add(s);
                }
            }
            if (ans.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "填空题每个空至少 1 个非空答案");
            }
            storedBlanks.add(ans);
        }
        Map<String, Object> config = req.getFillConfig() != null
                ? new LinkedHashMap<>(req.getFillConfig())
                : defaultFillConfig();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("blanks", storedBlanks);
        answer.put("config", config);
        item.setOptions(null);
        item.setAnswer(answer);
    }

    private static void applyEssay(ItemSaveReq req, QuesItem item) {
        String reference = req.getAnswer() == null ? null : strVal(req.getAnswer().get("reference"));
        if (reference == null || reference.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "简答题须配置参考答案 reference");
        }
        List<String> points = new ArrayList<>();
        Object pointsObj = req.getAnswer().get("keyPoints");
        if (pointsObj instanceof List<?> list) {
            for (Object p : list) {
                if (p != null && !p.toString().isBlank()) {
                    points.add(p.toString());
                }
            }
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("reference", reference);
        answer.put("points", points);
        item.setOptions(null);
        item.setAnswer(answer);
    }

    public static Map<String, Object> defaultFillConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ignoreCase", true);
        config.put("ignoreWidth", true);
        config.put("blankOrdered", false);
        return config;
    }

    /**
     * 实体 → 详情 VO（答案/选项转回 API 结构）
     */
    public static ItemDetailVO toDetailVO(QuesItem item, String bankName) {
        ItemDetailVO vo = new ItemDetailVO();
        vo.setItemId(item.getId());
        vo.setBankId(item.getBankId());
        vo.setBankName(bankName);
        vo.setQuesType(QuesTypeEnum.toApi(item.getQuesType()));
        vo.setStem(item.getStem());
        vo.setAnalysis(item.getAnalysis());
        vo.setDifficulty(DifficultyEnum.toApi(item.getDifficulty()));
        vo.setTags(item.getKnowledgeTags());
        vo.setScoreSuggest(item.getScoreSuggest());
        vo.setSource(item.getSource());
        vo.setSourceLaw(item.getSourceLaw());
        vo.setSourceChapter(item.getSourceChapter());
        vo.setStatus(ItemStatusEnum.toApi(item.getStatus()));
        vo.setUseCount(item.getUseCount());
        vo.setCorrectRate(item.getRightRate());
        vo.setAuditBy(item.getAuditBy());
        vo.setAuditTime(item.getAuditTime());
        vo.setCreatedBy(item.getCreateBy());
        vo.setCreatedTime(item.getCreateTime());
        vo.setUpdatedTime(item.getUpdateTime());

        QuesTypeEnum type = QuesTypeEnum.ofCode(item.getQuesType());
        Map<String, Object> answer = item.getAnswer() == null ? Map.of() : item.getAnswer();
        if (type != null && type.isChoice()) {
            vo.setOptions(toApiOptions(item.getOptions(), strList(answer.get("keys"))));
            Map<String, Object> apiAnswer = new LinkedHashMap<>();
            apiAnswer.put("keys", strList(answer.get("keys")));
            vo.setAnswer(apiAnswer);
        } else if (type == QuesTypeEnum.JUDGE) {
            List<String> keys = strList(answer.get("keys"));
            vo.setOptions(toApiOptions(item.getOptions(), keys));
            Map<String, Object> apiAnswer = new LinkedHashMap<>();
            apiAnswer.put("value", keys.isEmpty() ? null : keys.get(0));
            vo.setAnswer(apiAnswer);
        } else if (type == QuesTypeEnum.FILL) {
            Object configObj = answer.get("config");
            Map<String, Object> config = configObj instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m) : defaultFillConfig();
            vo.setFillConfig(config);
            boolean ordered = Boolean.TRUE.equals(config.get("blankOrdered"));
            List<Map<String, Object>> apiBlanks = new ArrayList<>();
            Object blanksObj = answer.get("blanks");
            if (blanksObj instanceof List<?> blanks) {
                int no = 1;
                for (Object b : blanks) {
                    Map<String, Object> blank = new LinkedHashMap<>();
                    blank.put("no", no++);
                    blank.put("answers", b instanceof List<?> ? strList(b) : List.of());
                    blank.put("ordered", ordered);
                    apiBlanks.add(blank);
                }
            }
            Map<String, Object> apiAnswer = new LinkedHashMap<>();
            apiAnswer.put("blanks", apiBlanks);
            vo.setAnswer(apiAnswer);
        } else if (type == QuesTypeEnum.ESSAY) {
            Map<String, Object> apiAnswer = new LinkedHashMap<>();
            apiAnswer.put("reference", answer.get("reference"));
            apiAnswer.put("keyPoints", strList(answer.get("points")));
            vo.setAnswer(apiAnswer);
        }
        return vo;
    }

    private static List<ItemDetailVO.OptionVO> toApiOptions(List<Map<String, Object>> storedOptions, List<String> answerKeys) {
        List<ItemDetailVO.OptionVO> list = new ArrayList<>();
        if (storedOptions != null) {
            for (Map<String, Object> o : storedOptions) {
                ItemDetailVO.OptionVO vo = new ItemDetailVO.OptionVO();
                String key = strVal(o.get("key"));
                vo.setOptKey(key);
                vo.setContent(strVal(o.get("content")));
                vo.setIsAnswer(answerKeys != null && answerKeys.contains(key));
                list.add(vo);
            }
        }
        return list;
    }

    private static String strVal(Object o) {
        return o == null ? null : o.toString();
    }

    private static List<String> strList(Object o) {
        List<String> list = new ArrayList<>();
        if (o instanceof List<?> raw) {
            for (Object e : raw) {
                if (e != null) {
                    list.add(e.toString());
                }
            }
        } else if (o != null) {
            list.add(o.toString());
        }
        return list;
    }
}
