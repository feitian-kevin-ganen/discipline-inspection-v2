package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.util.JsonUtils;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.entity.PaperQuestion;
import com.group.exam.exam.mapper.PaperQuestionMapper;
import com.group.exam.exam.support.QuesSnapshots;
import com.group.exam.exam.support.RandomStrategy;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.service.QuesItemService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 答卷试卷快照生成（固定卷 / 随机卷一人一卷 / 随机卷全场同卷）
 *
 * <p>快照结构：{paperId, paperName, paperType, totalScore, passScore, multiJudgeRule,
 * gradeRuleId, gradeRuleItems, questions:[{seq, quesId, quesType, score, stem, options, answer,
 * analysis, difficulty, optionOrderMap?}]}。answer/analysis 仅服务端留存，考生端脱敏。
 */
@Service
public class PaperSnapshotService {

    /** 全场同卷 Redis 缓存 key 前缀（value 为 [{quesId, score}] JSON） */
    public static final String SHARED_PAPER_KEY = "exam:paper:shared:";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] OPTION_KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final PaperQuestionMapper paperQuestionMapper;
    private final QuesItemService quesItemService;
    private final StringRedisTemplate redisTemplate;

    public PaperSnapshotService(PaperQuestionMapper paperQuestionMapper, QuesItemService quesItemService,
                                StringRedisTemplate redisTemplate) {
        this.paperQuestionMapper = paperQuestionMapper;
        this.quesItemService = quesItemService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 为考生生成本场答卷快照（按场次策略快照，不再读试卷现值）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildForCandidate(ExamSession session) {
        Map<String, Object> strategy = session.getPaperStrategySnapshot() == null
                ? Map.of() : session.getPaperStrategySnapshot();
        int paperType = strategy.get("paperType") instanceof Number n ? n.intValue() : 1;

        List<Map<String, Object>> questions;
        if (paperType == 1) {
            questions = fixedQuestions(session.getPaperId());
        } else {
            List<Map<String, Object>> rawStrategy = (List<Map<String, Object>>) strategy.get("randomStrategy");
            List<RandomStrategy.Rule> rules = RandomStrategy.parse(rawStrategy);
            boolean samePaper = strategy.get("samePaper") instanceof Number n && n.intValue() == 1;
            questions = samePaper ? sharedRandomQuestions(session, rules) : drawRandomQuestions(rules);
        }

        // 题序乱序（Fisher-Yates，结果固化进快照）
        if (session.getShuffleQuestion() != null && session.getShuffleQuestion() == 1) {
            Collections.shuffle(questions, RANDOM);
        }
        int seq = 0;
        for (Map<String, Object> q : questions) {
            q.put("seq", ++seq);
        }
        // 选项乱序（仅单选/多选；展示 key 重编，映射固化 optionOrderMap）
        if (session.getShuffleOption() != null && session.getShuffleOption() == 1) {
            for (Map<String, Object> q : questions) {
                shuffleOptions(q);
            }
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paperId", session.getPaperId());
        snapshot.put("paperName", session.getPaperName());
        snapshot.put("paperType", paperType);
        snapshot.put("totalScore", strategy.get("totalScore"));
        snapshot.put("passScore", strategy.get("passScore"));
        snapshot.put("multiJudgeRule", strategy.get("multiJudgeRule"));
        snapshot.put("gradeRuleId", strategy.get("gradeRuleId"));
        snapshot.put("gradeRuleItems", strategy.get("gradeRuleItems"));
        snapshot.put("questions", questions);
        return snapshot;
    }

    /** 固定卷：直接从 t_paper_question 快照行组装 */
    private List<Map<String, Object>> fixedQuestions(Long paperId) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (PaperQuestion pq : paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>()
                .eq("paper_id", paperId).orderByAsc("sort_no"))) {
            Map<String, Object> q = new LinkedHashMap<>(pq.getQuesSnapshot() == null ? Map.of() : pq.getQuesSnapshot());
            q.put("quesId", pq.getQuesId());
            q.put("quesType", pq.getQuesType());
            q.put("score", pq.getScore());
            questions.add(q);
        }
        if (questions.isEmpty()) {
            throw new BizException(ErrorCode.PAPER_SCORE_MISMATCH, "固定卷未配置题目，无法开考");
        }
        return questions;
    }

    /** 一人一卷：逐规则抽题（跨规则去重，不足 40006） */
    private List<Map<String, Object>> drawRandomQuestions(List<RandomStrategy.Rule> rules) {
        List<Map<String, Object>> questions = new ArrayList<>();
        Set<Long> drawn = new HashSet<>();
        for (RandomStrategy.Rule r : rules) {
            List<QuesItem> items = quesItemService.drawRandom(
                    r.bankIds(), r.includeChildren(), r.quesType(), r.difficulty(), r.count());
            appendDrawn(questions, drawn, items, r);
        }
        return questions;
    }

    /** 全场同卷：首个考生抽题后缓存 Redis，后续考生复用同一套题 */
    private List<Map<String, Object>> sharedRandomQuestions(ExamSession session, List<RandomStrategy.Rule> rules) {
        String key = SHARED_PAPER_KEY + session.getId();
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            List<Map<String, Object>> drawn = drawRandomQuestions(rules);
            List<Map<String, Object>> idList = drawn.stream()
                    .map(q -> Map.<String, Object>of("quesId", q.get("quesId"), "score", q.get("score")))
                    .toList();
            long ttlSec = Math.max(3600,
                    Duration.between(LocalDateTime.now(), session.getEndTime().plusHours(2)).getSeconds());
            redisTemplate.opsForValue().setIfAbsent(key, JsonUtils.toJson(idList), Duration.ofSeconds(ttlSec));
            cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return drawn;
            }
        }
        List<Map<String, Object>> idList = JsonUtils.fromJson(cached,
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        List<Long> ids = idList.stream().map(m -> RandomStrategy.asLong(m.get("quesId"))).toList();
        Map<Long, QuesItem> itemMap = quesItemService.listByIds(ids).stream()
                .collect(Collectors.toMap(QuesItem::getId, Function.identity()));
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Map<String, Object> entry : idList) {
            QuesItem item = itemMap.get(RandomStrategy.asLong(entry.get("quesId")));
            if (item == null) {
                continue;
            }
            Map<String, Object> q = QuesSnapshots.of(item);
            q.put("score", RandomStrategy.asBigDecimal(entry.get("score")));
            questions.add(q);
        }
        return questions;
    }

    private void appendDrawn(List<Map<String, Object>> questions, Set<Long> drawn,
                             List<QuesItem> items, RandomStrategy.Rule r) {
        int added = 0;
        for (QuesItem item : items) {
            if (drawn.add(item.getId())) {
                Map<String, Object> q = QuesSnapshots.of(item);
                q.put("score", r.score());
                questions.add(q);
                added++;
            }
        }
        if (added < r.count()) {
            // 跨规则重复导致缺口时从候选池补足
            List<QuesItem> pool = quesItemService.listEnabledByBanks(
                    r.bankIds(), r.includeChildren(), r.quesType(), r.difficulty());
            Collections.shuffle(pool, RANDOM);
            for (QuesItem item : pool) {
                if (added >= r.count()) {
                    break;
                }
                if (drawn.add(item.getId())) {
                    Map<String, Object> q = QuesSnapshots.of(item);
                    q.put("score", r.score());
                    questions.add(q);
                    added++;
                }
            }
        }
        if (added < r.count()) {
            throw new BizException(ErrorCode.RANDOM_STRATEGY_INSUFFICIENT,
                    "抽题策略可用题量不足：" + PaperService.ruleDesc(r) + " 需" + r.count() + "道");
        }
    }

    /** 选项乱序：重排展示 key（A/B/C...），optionOrderMap 记录 {展示key→原始key} */
    @SuppressWarnings("unchecked")
    private void shuffleOptions(Map<String, Object> q) {
        Object typeObj = q.get("quesType");
        int quesType = typeObj instanceof Number n ? n.intValue() : -1;
        if (quesType != 1 && quesType != 2) {
            return;
        }
        Object optionsObj = q.get("options");
        if (!(optionsObj instanceof List<?> options) || options.size() <= 1) {
            return;
        }
        List<Map<String, Object>> shuffled = new ArrayList<>();
        for (Object o : options) {
            if (o instanceof Map<?, ?> opt) {
                shuffled.add(new LinkedHashMap<>((Map<String, Object>) opt));
            }
        }
        Collections.shuffle(shuffled, RANDOM);
        Map<String, String> orderMap = new LinkedHashMap<>();
        for (int i = 0; i < shuffled.size(); i++) {
            Map<String, Object> opt = shuffled.get(i);
            String originalKey = opt.get("key") != null ? String.valueOf(opt.get("key"))
                    : String.valueOf(opt.get("optKey"));
            String displayKey = String.valueOf(OPTION_KEYS[i]);
            opt.put("key", displayKey);
            orderMap.put(displayKey, originalKey);
        }
        q.put("options", shuffled);
        q.put("optionOrderMap", orderMap);
    }
}
