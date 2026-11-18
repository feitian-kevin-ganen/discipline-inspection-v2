package com.group.exam.practice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.common.util.JsonUtils;
import com.group.exam.exam.service.GradingService;
import com.group.exam.practice.dto.PracticeAnswerReq;
import com.group.exam.practice.dto.PracticeStartReq;
import com.group.exam.practice.entity.PracticeRecord;
import com.group.exam.practice.mapper.PracticeRecordMapper;
import com.group.exam.question.entity.QuesBank;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.ItemStatusEnum;
import com.group.exam.question.service.QuesBankService;
import com.group.exam.question.service.QuesItemService;
import com.group.exam.question.support.StemTextUtils;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.SysConfigService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 日常练习（PRAC-01~04，V1.1 新增）
 *
 * <p>练习无会话表：发起为无状态抽题不落库；作答每题即时判定并落一行 t_practice_record（只增不改）。
 * 判定口径复用考试判分：选择/判断精确比对选项 key 集合；填空复用
 * {@link GradingService#fillHitStat}（归一化逐空比对）；简答不支持自动判定，
 * 返回 isRight=null 与参考答案（记录 is_right 落 0）。
 */
@Service
public class PracticeService extends ServiceImpl<PracticeRecordMapper, PracticeRecord> {

    /** 练习发起接口限流 key 前缀：10 次/分钟/用户（PRAC-01，10006）。
     * 作答接口（PRAC-02）不限流：clientEventId 幂等键已防重复提交，
     * 20 题模式连续作答属正常业务流量，按次限流必然误伤 */
    private static final String RATE_LIMIT_PREFIX = "exam:ratelimit:practice:";
    private static final int RATE_LIMIT_MAX = 10;
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60;

    /** 作答幂等结果 key 前缀：exam:practice:idem:{userId}:{clientEventId}，保留 24h */
    private static final String IDEM_PREFIX = "exam:practice:idem:";
    private static final long IDEM_TTL_HOURS = 24;

    /** 练习模式：1分类练习(CATEGORY) 2随机抽题(RANDOM) */
    private static final int MODE_CATEGORY = 1;
    private static final int MODE_RANDOM = 2;

    private final QuesItemService quesItemService;
    private final QuesBankService quesBankService;
    private final OrgUnitService orgUnitService;
    private final SysConfigService sysConfigService;
    private final StringRedisTemplate redisTemplate;

    public PracticeService(QuesItemService quesItemService, QuesBankService quesBankService,
                           OrgUnitService orgUnitService, SysConfigService sysConfigService,
                           StringRedisTemplate redisTemplate) {
        this.quesItemService = quesItemService;
        this.quesBankService = quesBankService;
        this.orgUnitService = orgUnitService;
        this.sysConfigService = sysConfigService;
        this.redisTemplate = redisTemplate;
    }

    // ---------------------------------------------------------------- 一级法规分类（练习发起页分类选择）

    /**
     * 一级法规分类列表（parent_id IS NULL、status=1，按 sort_no 排序），
     * 每项附该分类下启用（status=2，未删除）的题目数；
     * 练习按单个分类抽题，无启用题目的分类（如仅含子分类的旧演示库）对考生无意义，不返回
     */
    public List<Map<String, Object>> banks() {
        QueryWrapper<QuesBank> qw = new QueryWrapper<>();
        qw.isNull("parent_id").eq("status", 1).orderByAsc("sort_no");
        List<QuesBank> bankList = quesBankService.list(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (QuesBank bank : bankList) {
            long quesCount = quesItemService.count(new QueryWrapper<QuesItem>()
                    .eq("bank_id", bank.getId())
                    .eq("status", ItemStatusEnum.ENABLED.getCode()));
            if (quesCount == 0) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bankId", bank.getId());
            m.put("bankName", bank.getBankName());
            m.put("quesCount", quesCount);
            result.add(m);
        }
        return result;
    }

    // ---------------------------------------------------------------- PRAC-01 发起练习（无状态抽题）

    public Map<String, Object> start(PracticeStartReq req) {
        checkRateLimit(SecurityUtils.getUserId());
        PracticeStartReq safe = req == null ? new PracticeStartReq() : req;
        String mode = StringUtils.hasText(safe.getMode()) ? safe.getMode().trim().toUpperCase() : "RANDOM";
        if (!"CATEGORY".equals(mode) && !"RANDOM".equals(mode)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mode 仅支持 CATEGORY/RANDOM");
        }
        String bankName = null;
        List<Long> bankIds = null;
        if ("CATEGORY".equals(mode)) {
            if (safe.getBankId() == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "分类练习 bankId 必填");
            }
            QuesBank bank = quesBankService.getById(safe.getBankId());
            if (bank == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "题库分类不存在: " + safe.getBankId());
            }
            bankName = bank.getBankName();
            bankIds = List.of(bank.getId());
        }
        int count = safe.getCount() == null
                ? sysConfigService.getInt("practice.pageSize", 10) : safe.getCount();
        if (count != 5 && count != 10 && count != 20) {
            throw new BizException(ErrorCode.PARAM_ERROR, "count 仅支持 5/10/20");
        }
        int maxCount = sysConfigService.getInt("practice.maxCount", 50);
        if (count > maxCount) {
            throw new BizException(ErrorCode.PARAM_ERROR, "count 超过上限 practice.maxCount=" + maxCount);
        }
        List<Integer> difficulties = parseDifficulty(safe.getDifficulty());

        // 抽题范围：本期启用(ENABLED)的单选题；候选不足 count 抛 70002 不降级少抽
        List<QuesItem> items = quesItemService.drawPractice(bankIds, 1, difficulties, count);
        if (items.size() < count) {
            throw new BizException(ErrorCode.PRACTICE_QUES_INSUFFICIENT,
                    "可用题量不足：需 " + count + " 道，实际 " + items.size() + " 道");
        }

        List<Map<String, Object>> questions = new ArrayList<>();
        int seq = 0;
        for (QuesItem item : items) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("quesItemId", item.getId());
            q.put("seq", ++seq);
            q.put("stem", item.getStem());
            q.put("options", sanitizeOptions(item.getOptions()));
            q.put("sourceLaw", item.getSourceLaw());
            q.put("sourceChapter", item.getSourceChapter());
            q.put("knowledgePoint", firstTag(item));
            q.put("difficulty", difficultyToApi(item.getDifficulty()));
            questions.add(q);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("count", count);
        result.put("bankName", bankName);
        result.put("questions", questions);
        return result;
    }

    /** 选项脱敏：仅 optKey/content，不含答案与解析；练习场景选项不打乱（按题库原始顺序） */
    private static List<Map<String, Object>> sanitizeOptions(List<Map<String, Object>> options) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (options != null) {
            for (Map<String, Object> opt : options) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("optKey", opt.get("key") != null ? opt.get("key") : opt.get("optKey"));
                o.put("content", opt.get("content"));
                result.add(o);
            }
        }
        return result;
    }

    /** 难度过滤：BASIC→1，ADVANCED→IN(2,3)，不传不限 */
    private static List<Integer> parseDifficulty(String difficulty) {
        if (!StringUtils.hasText(difficulty)) {
            return null;
        }
        return switch (difficulty.trim().toUpperCase()) {
            case "BASIC" -> List.of(1);
            case "ADVANCED" -> List.of(2, 3);
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "difficulty 仅支持 BASIC/ADVANCED");
        };
    }

    /** 难度 API 编码：1→BASIC，2/3→ADVANCED（V1.1 两级口径） */
    private static String difficultyToApi(Integer difficulty) {
        return difficulty != null && difficulty == 1 ? "BASIC" : "ADVANCED";
    }

    private static String firstTag(QuesItem item) {
        return item.getKnowledgeTags() == null || item.getKnowledgeTags().isEmpty()
                ? null : item.getKnowledgeTags().get(0);
    }

    // ---------------------------------------------------------------- PRAC-02 提交单题作答（即时判定 + 落记录）

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> answer(PracticeAnswerReq req) {
        Long userId = SecurityUtils.getUserId();
        // 作答接口不限流：幂等键防重，连续答题为正常流量（原 10 次/分钟口径会误伤 20 题模式）
        if (req == null || req.getQuesItemId() == null || req.getAnswer() == null
                || !StringUtils.hasText(req.getClientEventId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "quesItemId/answer/clientEventId 必填");
        }
        int practiceMode = parseMode(req.getMode());

        // 幂等：同一 clientEventId 重复提交返回首次判分结果（code=0），不重复落行
        String idemKey = IDEM_PREFIX + userId + ":" + req.getClientEventId().trim();
        String cached = redisTemplate.opsForValue().get(idemKey);
        if (cached != null) {
            return JsonUtils.fromJson(cached, new TypeReference<>() {
            });
        }

        QuesItem item = quesItemService.getEnabledItem(req.getQuesItemId());
        if (item == null) {
            throw new BizException(ErrorCode.QUESTION_NOT_FOUND);
        }
        Map<String, Object> userAnswer = canonicalAnswer(req.getAnswer());
        JudgeOutcome outcome = judge(item, userAnswer);

        // 落一行练习记录（组织/来源法规取作答时快照）
        LoginUser loginUser = SecurityUtils.getLoginUser();
        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setUserName(loginUser.getUserName());
        record.setOrgId(loginUser.getOrgId());
        record.setOrgPath(loginUser.getOrgPath());
        OrgUnit unit = loginUser.getOrgId() == null ? null : orgUnitService.getUnit(loginUser.getOrgId());
        record.setOrgName(unit == null ? null : unit.getUnitName());
        record.setQuesItemId(item.getId());
        record.setQuesType(item.getQuesType());
        record.setBankId(req.getBankId());
        record.setSourceLaw(item.getSourceLaw());
        record.setSourceChapter(item.getSourceChapter());
        record.setPracticeMode(practiceMode);
        record.setUserAnswer(userAnswer);
        record.setIsRight(Boolean.TRUE.equals(outcome.isRight()) ? 1 : 0);
        record.setDurationSec(req.getDurationSec());
        record.setCreateBy(userId);
        save(record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", record.getId());
        result.put("quesItemId", item.getId());
        result.put("isRight", outcome.isRight());
        result.put("correctAnswer", outcome.correctAnswer());
        result.put("analysis", item.getAnalysis());
        result.put("sourceLaw", item.getSourceLaw());
        result.put("sourceChapter", item.getSourceChapter());
        result.put("knowledgePoint", firstTag(item));
        if (outcome.reference() != null) {
            // 简答不支持自动判定：仅返回参考答案，isRight=null
            result.put("reference", outcome.reference());
        }
        redisTemplate.opsForValue().set(idemKey, JsonUtils.toJson(result),
                IDEM_TTL_HOURS, TimeUnit.HOURS);
        return result;
    }

    private record JudgeOutcome(Boolean isRight, Object correctAnswer, String reference) {
    }

    /** 即时判定：选择/判断 key 集合精确比对；填空复用考试判分口径；简答不自动判定 */
    @SuppressWarnings("unchecked")
    private JudgeOutcome judge(QuesItem item, Map<String, Object> userAnswer) {
        Map<String, Object> stdAnswer = item.getAnswer() == null ? Map.of() : item.getAnswer();
        int type = item.getQuesType() == null ? -1 : item.getQuesType();
        switch (type) {
            case 1, 2, 3 -> {
                List<String> standard = strList(stdAnswer.get("keys"));
                boolean right = !standard.isEmpty()
                        && new TreeSet<>(strList(userAnswer.get("keys"))).equals(new TreeSet<>(standard));
                return new JudgeOutcome(right, standard, null);
            }
            case 4 -> {
                int[] hitStat = GradingService.fillHitStat(stdAnswer, strList(userAnswer.get("blanks")));
                boolean right = hitStat[1] > 0 && hitStat[0] == hitStat[1];
                return new JudgeOutcome(right, stdAnswer.get("blanks"), null);
            }
            case 5 -> {
                return new JudgeOutcome(null, null,
                        stdAnswer.get("reference") == null ? null : String.valueOf(stdAnswer.get("reference")));
            }
            default -> throw new BizException(ErrorCode.QUESTION_NOT_FOUND, "题目类型无法识别");
        }
    }

    /** 答案结构归一：{selected}→{keys}；blanks/text 原样（口径同考试答卷保存） */
    private static Map<String, Object> canonicalAnswer(Map<String, Object> answer) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        Object keys = answer.containsKey("keys") ? answer.get("keys") : answer.get("selected");
        if (keys != null) {
            canonical.put("keys", keys);
        }
        if (answer.get("blanks") != null) {
            canonical.put("blanks", answer.get("blanks"));
        }
        if (answer.get("text") != null) {
            canonical.put("text", answer.get("text"));
        }
        return canonical;
    }

    private static List<String> strList(Object o) {
        List<String> list = new ArrayList<>();
        if (o instanceof List<?> raw) {
            for (Object e : raw) {
                if (e != null) {
                    list.add(String.valueOf(e));
                }
            }
        } else if (o != null) {
            list.add(String.valueOf(o));
        }
        return list;
    }

    private static int parseMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mode 必填（CATEGORY/RANDOM）");
        }
        return switch (mode.trim().toUpperCase()) {
            case "CATEGORY" -> MODE_CATEGORY;
            case "RANDOM" -> MODE_RANDOM;
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "mode 仅支持 CATEGORY/RANDOM");
        };
    }

    // ---------------------------------------------------------------- PRAC-03 练习记录（本人，分页）

    public PageResult<Map<String, Object>> records(String sourceLaw, Boolean onlyWrong,
                                                   long pageNum, long pageSize) {
        Long userId = SecurityUtils.getUserId();
        QueryWrapper<PracticeRecord> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        qw.eq(StringUtils.hasText(sourceLaw), "source_law", sourceLaw == null ? null : sourceLaw.trim());
        qw.eq(Boolean.TRUE.equals(onlyWrong), "is_right", 0);
        qw.orderByDesc("id");
        Page<PracticeRecord> page = page(new Page<>(pageNum, pageSize), qw);

        List<Long> quesIds = page.getRecords().stream().map(PracticeRecord::getQuesItemId).distinct().toList();
        Map<Long, QuesItem> itemMap = quesIds.isEmpty() ? Map.of()
                : quesItemService.listByIds(quesIds).stream()
                .collect(Collectors.toMap(QuesItem::getId, i -> i, (a, b) -> a));
        boolean expand = Boolean.TRUE.equals(onlyWrong);
        return PageResult.of(page).map(r -> toRecordItem(r, itemMap.get(r.getQuesItemId()), expand));
    }

    /** 记录列表项；onlyWrong=true（错题回顾）时额外展开题干/选项/答案/解析/知识点（仅本人历史练习） */
    private static Map<String, Object> toRecordItem(PracticeRecord r, QuesItem item, boolean expand) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordId", r.getId());
        m.put("quesItemId", r.getQuesItemId());
        m.put("stemText", item == null ? null : StemTextUtils.summary(item.getStem(), 100));
        m.put("myAnswer", r.getUserAnswer());
        m.put("isRight", r.getIsRight() != null && r.getIsRight() == 1);
        m.put("sourceLaw", r.getSourceLaw());
        m.put("sourceChapter", r.getSourceChapter());
        m.put("practiceMode", r.getPracticeMode() != null && r.getPracticeMode() == MODE_CATEGORY
                ? "CATEGORY" : "RANDOM");
        m.put("durationSec", r.getDurationSec());
        m.put("answerTime", r.getCreateTime());
        if (expand && item != null) {
            m.put("stem", item.getStem());
            m.put("options", sanitizeOptions(item.getOptions()));
            m.put("correctAnswer", item.getAnswer());
            m.put("analysis", item.getAnalysis());
            m.put("knowledgePoint", firstTag(item));
        }
        return m;
    }

    // ---------------------------------------------------------------- PRAC-04 练习统计（本人，实时聚合）

    public Map<String, Object> stat(String periodType, String startDate, String endDate) {
        Long userId = SecurityUtils.getUserId();
        LocalDateTime[] range = resolveRange(periodType, startDate, endDate);
        QueryWrapper<PracticeRecord> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        qw.ge(range[0] != null, "create_time", range[0]);
        qw.lt(range[1] != null, "create_time", range[1]);
        List<PracticeRecord> records = list(qw);

        long total = records.size();
        long right = records.stream().filter(r -> r.getIsRight() != null && r.getIsRight() == 1).count();
        long practiceDays = records.stream().filter(r -> r.getCreateTime() != null)
                .map(r -> r.getCreateTime().toLocalDate()).distinct().count();

        // 按来源法规（快照）分组正确率
        Map<String, List<PracticeRecord>> byLaw = records.stream()
                .collect(Collectors.groupingBy(r -> r.getSourceLaw() == null ? "未标注" : r.getSourceLaw(),
                        LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> byLawList = new ArrayList<>();
        byLaw.forEach((law, group) -> {
            long lawRight = group.stream().filter(r -> r.getIsRight() != null && r.getIsRight() == 1).count();
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("sourceLaw", law);
            e.put("questions", group.size());
            e.put("accuracy", ratio(lawRight, group.size()));
            byLawList.add(e);
        });
        byLawList.sort(Comparator.comparing((Map<String, Object> e) -> (BigDecimal) e.get("accuracy"))
                .thenComparing(e -> (Integer) e.get("questions"), Comparator.reverseOrder()));

        // 按日趋势
        Map<LocalDate, List<PracticeRecord>> byDay = records.stream()
                .filter(r -> r.getCreateTime() != null)
                .collect(Collectors.groupingBy(r -> r.getCreateTime().toLocalDate()));
        List<Map<String, Object>> trend = new ArrayList<>();
        byDay.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            long dayRight = entry.getValue().stream()
                    .filter(r -> r.getIsRight() != null && r.getIsRight() == 1).count();
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("date", entry.getKey().toString());
            e.put("questions", entry.getValue().size());
            e.put("accuracy", ratio(dayRight, entry.getValue().size()));
            trend.add(e);
        });

        // 薄弱知识点 TOP10：按题目现值 knowledge_tags 聚合错误率降序
        List<Long> quesIds = records.stream().map(PracticeRecord::getQuesItemId).distinct().toList();
        Map<Long, List<String>> tagsByQues = quesIds.isEmpty() ? Map.of()
                : quesItemService.listByIds(quesIds).stream()
                .collect(Collectors.toMap(QuesItem::getId,
                        i -> i.getKnowledgeTags() == null ? List.of() : i.getKnowledgeTags(), (a, b) -> a));
        Map<String, long[]> tagStat = new HashMap<>(); // tag → [wrong, total]
        for (PracticeRecord r : records) {
            for (String tag : tagsByQues.getOrDefault(r.getQuesItemId(), List.of())) {
                long[] stat = tagStat.computeIfAbsent(tag, k -> new long[2]);
                stat[1]++;
                if (r.getIsRight() != null && r.getIsRight() == 0) {
                    stat[0]++;
                }
            }
        }
        List<Map<String, Object>> weakPoints = tagStat.entrySet().stream()
                .filter(e -> e.getValue()[0] > 0)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("knowledgePoint", e.getKey());
                    m.put("wrongCount", e.getValue()[0]);
                    m.put("wrongRate", ratio(e.getValue()[0], e.getValue()[1]));
                    return m;
                })
                .sorted(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("wrongRate"))
                        .reversed().thenComparing(m -> (Long) m.get("wrongCount"), Comparator.reverseOrder()))
                .limit(10)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalQuestions", total);
        result.put("rightCount", right);
        result.put("accuracy", ratio(right, total));
        result.put("practiceDays", practiceDays);
        result.put("byLaw", byLawList);
        result.put("trend", trend);
        result.put("weakPoints", weakPoints);
        return result;
    }

    /**
     * 统计时间范围：[起始时刻, 结束时刻)；periodType 为空表示全部累计；
     * CUSTOM 须传 startDate/endDate（yyyy-MM-dd），非法抛 60001
     */
    private static LocalDateTime[] resolveRange(String periodType, String startDate, String endDate) {
        if (!StringUtils.hasText(periodType)) {
            return new LocalDateTime[]{null, null};
        }
        LocalDate today = LocalDate.now();
        return switch (periodType.trim().toUpperCase()) {
            case "MONTH" -> new LocalDateTime[]{today.withDayOfMonth(1).atStartOfDay(), null};
            case "QUARTER" -> {
                int quarterStart = (today.getMonthValue() - 1) / 3 * 3 + 1;
                yield new LocalDateTime[]{LocalDate.of(today.getYear(), quarterStart, 1).atStartOfDay(), null};
            }
            case "YEAR" -> new LocalDateTime[]{today.withDayOfYear(1).atStartOfDay(), null};
            case "CUSTOM" -> {
                try {
                    LocalDate start = LocalDate.parse(startDate == null ? "" : startDate.trim());
                    LocalDate end = LocalDate.parse(endDate == null ? "" : endDate.trim());
                    if (end.isBefore(start)) {
                        throw new BizException(ErrorCode.STAT_PARAM_ERROR, "endDate 不能早于 startDate");
                    }
                    yield new LocalDateTime[]{start.atStartOfDay(), end.plusDays(1).atStartOfDay()};
                } catch (java.time.format.DateTimeParseException e) {
                    throw new BizException(ErrorCode.STAT_PARAM_ERROR, "CUSTOM 须传 startDate/endDate（yyyy-MM-dd）");
                }
            }
            default -> throw new BizException(ErrorCode.STAT_PARAM_ERROR,
                    "periodType 仅支持 MONTH/QUARTER/YEAR/CUSTOM");
        };
    }

    /** 0~1 比率（scale 2），分母为 0 返回 0 */
    private static BigDecimal ratio(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- 内部工具

    /** 按用户限流：10 次/分钟（口径同登录限流，仅用于发起练习），超限抛 10006 */
    private void checkRateLimit(Long userId) {
        String key = RATE_LIMIT_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > RATE_LIMIT_MAX) {
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
    }
}
