package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.group.exam.exam.entity.AnswerDetail;
import com.group.exam.exam.entity.AnswerSheet;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.entity.MarkTask;
import com.group.exam.exam.mapper.AnswerDetailMapper;
import com.group.exam.exam.mapper.AnswerSheetMapper;
import com.group.exam.exam.mapper.ExamSessionMapper;
import com.group.exam.exam.mapper.MarkTaskMapper;
import com.group.exam.exam.mapper.MarkerMapper;
import com.group.exam.exam.support.RandomStrategy;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.service.QuesItemService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 判分流水线（务实简化版：交卷/收卷时同步触发，直接服务调用）
 *
 * <p>客观题自动判分（detail mark_status=1）；填空部分命中标 3 待人工复核；
 * 简答题生成阅卷任务（round_no=1，MARKER 轮询分配，无 MARKER 时分配给场次创建人）；
 * 全部主观/复核完成后合成成绩（mark_status=4 + 等级快照）。
 */
@Service
public class GradingService {

    /** MARKER 轮询游标 */
    private static final String MARKER_RR_KEY = "exam:marker:rr";

    private final AnswerSheetMapper answerSheetMapper;
    private final AnswerDetailMapper answerDetailMapper;
    private final MarkTaskMapper markTaskMapper;
    private final ExamSessionMapper examSessionMapper;
    private final MarkerMapper markerMapper;
    private final GradeRuleService gradeRuleService;
    private final QuesItemService quesItemService;
    private final StringRedisTemplate redisTemplate;

    public GradingService(AnswerSheetMapper answerSheetMapper, AnswerDetailMapper answerDetailMapper,
                          MarkTaskMapper markTaskMapper, ExamSessionMapper examSessionMapper,
                          MarkerMapper markerMapper, GradeRuleService gradeRuleService,
                          QuesItemService quesItemService, StringRedisTemplate redisTemplate) {
        this.answerSheetMapper = answerSheetMapper;
        this.answerDetailMapper = answerDetailMapper;
        this.markTaskMapper = markTaskMapper;
        this.examSessionMapper = examSessionMapper;
        this.markerMapper = markerMapper;
        this.gradeRuleService = gradeRuleService;
        this.quesItemService = quesItemService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 交卷后触发：客观题判分 → 生成阅卷任务（如有）→ 纯客观卷直接合成成绩
     */
    @Transactional(rollbackFor = Exception.class)
    public void gradeSheet(Long sheetId) {
        AnswerSheet sheet = answerSheetMapper.selectById(sheetId);
        if (sheet == null || sheet.getMarkStatus() == null || sheet.getMarkStatus() != 1) {
            return;
        }
        List<AnswerDetail> details = answerDetailMapper.selectList(new QueryWrapper<AnswerDetail>()
                .eq("sheet_id", sheetId).orderByAsc("sort_no"));
        Map<Long, Map<String, Object>> questions = snapshotQuestions(sheet);

        boolean hasEssay = false;
        boolean hasReview = false;
        List<AnswerDetail> judged = new ArrayList<>();
        for (AnswerDetail detail : details) {
            if (detail.getMarkStatus() != null && detail.getMarkStatus() != 0) {
                if (detail.getMarkStatus() == 3) {
                    hasReview = true;
                }
                continue;
            }
            Map<String, Object> q = questions.get(detail.getQuesId());
            int type = detail.getQuesType() == null ? -1 : detail.getQuesType();
            switch (type) {
                case 1, 3 -> judgeChoice(detail, q, sheet);
                case 2 -> judgeMulti(detail, q, sheet);
                case 4 -> {
                    judgeFill(detail, q);
                    if (detail.getMarkStatus() != null && detail.getMarkStatus() == 3) {
                        hasReview = true;
                    }
                }
                case 5 -> hasEssay = true;
                default -> {
                }
            }
            if (type != 5 && detail.getMarkStatus() != null && detail.getMarkStatus() != 0) {
                judged.add(detail);
                answerDetailMapper.updateById(detail);
            }
        }

        BigDecimal objective = details.stream()
                .filter(d -> d.getQuesType() != null && d.getQuesType() != 5)
                .map(d -> d.getScore() == null ? BigDecimal.ZERO : d.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sheet.setObjectiveScore(objective);

        if (hasEssay || hasReview) {
            sheet.setMarkStatus(3);
            answerSheetMapper.updateById(sheet);
            createMarkTasks(sheet, details);
        } else {
            sheet.setMarkStatus(2);
            answerSheetMapper.updateById(sheet);
            synthesize(sheetId);
        }
        refreshQuesStats(judged);
    }

    // ---------------------------------------------------------------- 客观题判分

    /** 单选/判断：keys 与标准答案完全一致 → 满分，否则 0 */
    private void judgeChoice(AnswerDetail detail, Map<String, Object> q, AnswerSheet sheet) {
        Set<String> user = restoredKeys(detail, q);
        Set<String> standard = standardKeys(q);
        boolean right = !standard.isEmpty() && user.equals(standard);
        applyJudgeResult(detail, right ? detail.getQuesScore() : BigDecimal.ZERO, right ? 1 : 0, 1);
    }

    /**
     * 多选：1全对得分 / 2漏选半分（U⊂S 真子集半分 is_right=2，错选 0）/ 3漏选不得分
     */
    private void judgeMulti(AnswerDetail detail, Map<String, Object> q, AnswerSheet sheet) {
        Set<String> user = restoredKeys(detail, q);
        Set<String> standard = standardKeys(q);
        int rule = sheet.getPaperSnapshot() != null
                && sheet.getPaperSnapshot().get("multiJudgeRule") instanceof Number n ? n.intValue() : 1;
        if (!standard.isEmpty() && user.equals(standard)) {
            applyJudgeResult(detail, detail.getQuesScore(), 1, 1);
            return;
        }
        if (rule == 2 && !user.isEmpty() && standard.containsAll(user)) {
            applyJudgeResult(detail,
                    detail.getQuesScore().multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP), 2, 1);
            return;
        }
        applyJudgeResult(detail, BigDecimal.ZERO, 0, 1);
    }

    /**
     * 填空：归一化逐空比对（同义词数组命中其一即对）；全对满分 / 全错或未答 0 分自动判；
     * 部分对部分错 → 按比例暂记分并标 mark_status=3 待人工复核
     */
    private void judgeFill(AnswerDetail detail, Map<String, Object> q) {
        Map<String, Object> answer = q == null || !(q.get("answer") instanceof Map)
                ? Map.of() : castMap(q.get("answer"));
        List<String> userBlanks = new ArrayList<>();
        if (detail.getUserAnswer() != null && detail.getUserAnswer().get("blanks") instanceof List<?> list) {
            for (Object o : list) {
                userBlanks.add(o == null ? "" : String.valueOf(o));
            }
        }
        boolean unanswered = userBlanks.stream().allMatch(s -> s == null || s.isBlank());
        int[] hitStat = fillHitStat(answer, userBlanks);
        int hits = hitStat[0];
        int blankCount = hitStat[1];
        if (blankCount == 0 || unanswered) {
            applyJudgeResult(detail, BigDecimal.ZERO, 0, 1);
            return;
        }
        if (hits == blankCount) {
            applyJudgeResult(detail, detail.getQuesScore(), 1, 1);
        } else if (hits == 0) {
            applyJudgeResult(detail, BigDecimal.ZERO, 0, 1);
        } else {
            BigDecimal partial = detail.getQuesScore()
                    .multiply(BigDecimal.valueOf(hits))
                    .divide(BigDecimal.valueOf(blankCount), 2, RoundingMode.HALF_UP);
            // 部分对部分错：无法自动确定，标待人工复核（暂记分，复核结论覆盖）
            applyJudgeResult(detail, partial, 2, 3);
        }
    }

    /**
     * 填空比对（测试判分/练习即时判定共用口径）：解析标准答案 blanks 与 config 后逐空比对，
     * 返回 int[]{命中空数, 总空数}；无标准空时返回 {0, 0}。
     */
    @SuppressWarnings("unchecked")
    public static int[] fillHitStat(Map<String, Object> answer, List<String> userBlanks) {
        Map<String, Object> safeAnswer = answer == null ? Map.of() : answer;
        List<List<String>> stdBlanks = new ArrayList<>();
        Object blanksObj = safeAnswer.get("blanks");
        if (blanksObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof List<?> synonyms) {
                    List<String> one = new ArrayList<>();
                    for (Object s : synonyms) {
                        one.add(String.valueOf(s));
                    }
                    stdBlanks.add(one);
                }
            }
        }
        if (stdBlanks.isEmpty()) {
            return new int[]{0, 0};
        }
        Map<String, Object> config = safeAnswer.get("config") instanceof Map
                ? (Map<String, Object>) safeAnswer.get("config") : Map.of();
        boolean ignoreCase = boolOf(config.get("ignoreCase"), true);
        boolean ignoreWidth = boolOf(config.get("ignoreWidth"), true);
        Object orderedObj = config.containsKey("blankOrdered") ? config.get("blankOrdered") : config.get("ordered");
        boolean blankOrdered = boolOf(orderedObj, true);

        List<String> safeUser = userBlanks == null ? List.of() : userBlanks;
        int hits = blankOrdered
                ? orderedHits(safeUser, stdBlanks, ignoreCase, ignoreWidth)
                : unorderedHits(safeUser, stdBlanks, ignoreCase, ignoreWidth);
        return new int[]{hits, stdBlanks.size()};
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static int orderedHits(List<String> userBlanks, List<List<String>> stdBlanks,
                                   boolean ignoreCase, boolean ignoreWidth) {
        int hits = 0;
        for (int i = 0; i < stdBlanks.size(); i++) {
            String user = i < userBlanks.size() ? userBlanks.get(i) : "";
            String nu = norm(user, ignoreCase, ignoreWidth);
            if (nu.isEmpty()) {
                continue;
            }
            for (String std : stdBlanks.get(i)) {
                if (nu.equals(norm(std, ignoreCase, ignoreWidth))) {
                    hits++;
                    break;
                }
            }
        }
        return hits;
    }

    /** 多空乱序：空数较小，全排列取最大命中数 */
    private static int unorderedHits(List<String> userBlanks, List<List<String>> stdBlanks,
                                     boolean ignoreCase, boolean ignoreWidth) {
        int n = stdBlanks.size();
        List<String> normalizedUser = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            normalizedUser.add(norm(i < userBlanks.size() ? userBlanks.get(i) : "", ignoreCase, ignoreWidth));
        }
        List<Set<String>> normalizedStd = new ArrayList<>();
        for (List<String> synonyms : stdBlanks) {
            Set<String> set = new HashSet<>();
            for (String s : synonyms) {
                set.add(norm(s, ignoreCase, ignoreWidth));
            }
            normalizedStd.add(set);
        }
        return maxMatch(normalizedUser, normalizedStd, new boolean[n], 0);
    }

    private static int maxMatch(List<String> user, List<Set<String>> std, boolean[] used, int depth) {
        if (depth == user.size()) {
            return 0;
        }
        int best = 0;
        for (int i = 0; i < std.size(); i++) {
            if (used[i]) {
                continue;
            }
            used[i] = true;
            int hit = !user.get(depth).isEmpty() && std.get(i).contains(user.get(depth)) ? 1 : 0;
            best = Math.max(best, hit + maxMatch(user, std, used, depth + 1));
            used[i] = false;
        }
        return best;
    }

    /** 归一化：trim → 全角转半角 → 忽略大小写 → 压缩空白 */
    static String norm(String s, boolean ignoreCase, boolean ignoreWidth) {
        if (s == null) {
            return "";
        }
        String r = s.trim();
        if (ignoreWidth) {
            StringBuilder sb = new StringBuilder(r.length());
            for (char c : r.toCharArray()) {
                if (c == '　') {
                    sb.append(' ');
                } else if (c >= '！' && c <= '～') {
                    sb.append((char) (c - 0xFEE0));
                } else {
                    sb.append(c);
                }
            }
            r = sb.toString();
        }
        if (ignoreCase) {
            r = r.toLowerCase();
        }
        return r.replaceAll("\\s+", " ").trim();
    }

    private void applyJudgeResult(AnswerDetail detail, BigDecimal score, int isRight, int markStatus) {
        detail.setScore(score);
        detail.setIsRight(isRight);
        detail.setMarkStatus(markStatus);
    }

    /** 考生展示 key 经选项乱序映射还原为原始 key 集合（优先取答卷明细快照 option_order，回退试卷快照 optionOrderMap） */
    @SuppressWarnings("unchecked")
    private Set<String> restoredKeys(AnswerDetail detail, Map<String, Object> q) {
        Set<String> keys = new TreeSet<>();
        if (detail.getUserAnswer() != null && detail.getUserAnswer().get("keys") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    keys.add(String.valueOf(o));
                }
            }
        }
        Object orderObj = detail.getOptionOrder() != null ? detail.getOptionOrder()
                : (q == null ? null : q.get("optionOrderMap"));
        if (orderObj instanceof Map<?, ?> map && !keys.isEmpty()) {
            Set<String> restored = new TreeSet<>();
            for (String key : keys) {
                Object original = ((Map<String, Object>) map).get(key);
                restored.add(original == null ? key : String.valueOf(original));
            }
            return restored;
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private Set<String> standardKeys(Map<String, Object> q) {
        Set<String> keys = new TreeSet<>();
        if (q != null && q.get("answer") instanceof Map<?, ?> answer
                && ((Map<String, Object>) answer).get("keys") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    keys.add(String.valueOf(o));
                }
            }
        }
        return keys;
    }

    // ---------------------------------------------------------------- 阅卷任务生成（MARKER 轮询分配）

    private void createMarkTasks(AnswerSheet sheet, List<AnswerDetail> details) {
        List<Map<String, Object>> markers = markerMapper.listMarkers();
        Long cursor = redisTemplate.opsForValue().increment(MARKER_RR_KEY);
        int offset = cursor == null ? 0 : (int) (cursor % Math.max(markers.size(), 1));
        ExamSession session = examSessionMapper.selectById(sheet.getSessionId());
        for (AnswerDetail detail : details) {
            boolean needTask = (detail.getQuesType() != null && detail.getQuesType() == 5)
                    || (detail.getMarkStatus() != null && detail.getMarkStatus() == 3);
            if (!needTask) {
                continue;
            }
            Long exists = markTaskMapper.selectCount(new QueryWrapper<MarkTask>()
                    .eq("detail_id", detail.getId()).eq("round_no", 1));
            if (exists != null && exists > 0) {
                continue;
            }
            MarkTask task = new MarkTask();
            task.setSessionId(sheet.getSessionId());
            task.setSheetId(sheet.getId());
            task.setDetailId(detail.getId());
            task.setQuesId(detail.getQuesId());
            if (markers.isEmpty()) {
                // 无阅卷教师时分配给场次创建人
                task.setMarkerId(session == null || session.getCreateBy() == null ? sheet.getCreateBy() : session.getCreateBy());
                task.setMarkerName("场次创建人");
            } else {
                Map<String, Object> marker = markers.get(offset++ % markers.size());
                task.setMarkerId(((Number) marker.get("id")).longValue());
                task.setMarkerName(String.valueOf(marker.get("userName")));
            }
            task.setRoundNo(1);
            task.setMarkStatus(0);
            task.setAssignBy(sheet.getCreateBy());
            markTaskMapper.insert(task);
        }
    }

    // ---------------------------------------------------------------- 成绩合成

    /**
     * 尝试合成成绩：答卷已交卷、无未判简答、无待复核、无待阅任务时合成（mark_status=4）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean trySynthesize(Long sheetId) {
        AnswerSheet sheet = answerSheetMapper.selectById(sheetId);
        if (sheet == null || sheet.getSheetStatus() == null || sheet.getSheetStatus() != 2) {
            return false;
        }
        if (sheet.getMarkStatus() != null && sheet.getMarkStatus() >= 4) {
            return true;
        }
        Long pendingDetails = answerDetailMapper.selectCount(new QueryWrapper<AnswerDetail>()
                .eq("sheet_id", sheetId)
                .and(w -> w.eq("mark_status", 3).or(q -> q.eq("mark_status", 0).eq("ques_type", 5))));
        if (pendingDetails != null && pendingDetails > 0) {
            return false;
        }
        Long pendingTasks = markTaskMapper.selectCount(new QueryWrapper<MarkTask>()
                .eq("sheet_id", sheetId).eq("mark_status", 0));
        if (pendingTasks != null && pendingTasks > 0) {
            return false;
        }
        synthesize(sheetId);
        return true;
    }

    /** 合成：total = 客观 + 主观，按快照等级区间命中等级，mark_status=4 */
    private void synthesize(Long sheetId) {
        AnswerSheet sheet = answerSheetMapper.selectById(sheetId);
        List<AnswerDetail> details = answerDetailMapper.selectList(
                new QueryWrapper<AnswerDetail>().eq("sheet_id", sheetId));
        BigDecimal objective = BigDecimal.ZERO;
        BigDecimal subjective = BigDecimal.ZERO;
        for (AnswerDetail d : details) {
            BigDecimal score = d.getScore() == null ? BigDecimal.ZERO : d.getScore();
            if (d.getQuesType() != null && d.getQuesType() == 5) {
                subjective = subjective.add(score);
            } else {
                objective = objective.add(score);
            }
        }
        BigDecimal total = objective.add(subjective).setScale(2, RoundingMode.HALF_UP);
        sheet.setObjectiveScore(objective.setScale(2, RoundingMode.HALF_UP));
        sheet.setSubjectiveScore(subjective.setScale(2, RoundingMode.HALF_UP));
        sheet.setTotalScore(total);

        Map<String, Object> snapshot = sheet.getPaperSnapshot() == null ? Map.of() : sheet.getPaperSnapshot();
        Object gradeRuleId = snapshot.get("gradeRuleId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gradeItems = snapshot.get("gradeRuleItems") instanceof List
                ? (List<Map<String, Object>>) snapshot.get("gradeRuleItems") : List.of();
        Map<String, Object> grade = gradeRuleService.resolveGrade(gradeItems, total);
        if (grade != null) {
            sheet.setGradeRuleId(RandomStrategy.asLong(gradeRuleId));
            sheet.setGradeCode(RandomStrategy.asString(grade.get("gradeCode")));
            sheet.setGradeName(RandomStrategy.asString(grade.get("gradeName")));
        }
        sheet.setMarkStatus(4);
        answerSheetMapper.updateById(sheet);
    }

    /** 题目统计冗余回写（简化：判分即维护 answer_count/right_count/right_rate） */
    private void refreshQuesStats(List<AnswerDetail> judged) {
        for (AnswerDetail d : judged) {
            if (d.getQuesId() == null || d.getMarkStatus() == null || d.getMarkStatus() == 3) {
                continue;
            }
            int right = d.getIsRight() != null && d.getIsRight() == 1 ? 1 : 0;
            quesItemService.update(new UpdateWrapper<QuesItem>()
                    .eq("id", d.getQuesId())
                    .setSql("answer_count = answer_count + 1, "
                            + "right_count = right_count + " + right + ", "
                            + "right_rate = ROUND((right_count + " + right + ") * 100.0 / (answer_count + 1), 2)"));
        }
    }

    @SuppressWarnings("unchecked")
    static Map<Long, Map<String, Object>> snapshotQuestions(AnswerSheet sheet) {
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        if (sheet.getPaperSnapshot() != null && sheet.getPaperSnapshot().get("questions") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> q) {
                    Map<String, Object> question = (Map<String, Object>) q;
                    Long quesId = RandomStrategy.asLong(question.get("quesId"));
                    if (quesId != null) {
                        map.put(quesId, question);
                    }
                }
            }
        }
        return map;
    }

    private static boolean boolOf(Object o, boolean def) {
        return o == null ? def : Boolean.parseBoolean(String.valueOf(o));
    }
}
