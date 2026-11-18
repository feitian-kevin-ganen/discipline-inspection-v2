package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.GradeRuleItemReq;
import com.group.exam.exam.dto.PaperCopyReq;
import com.group.exam.exam.dto.PaperQuestionsReq;
import com.group.exam.exam.dto.PaperSaveReq;
import com.group.exam.exam.dto.PaperUpdateReq;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.entity.Paper;
import com.group.exam.exam.entity.PaperQuestion;
import com.group.exam.exam.enums.MultiScoreRuleEnum;
import com.group.exam.exam.enums.PaperStatusEnum;
import com.group.exam.exam.mapper.ExamSessionMapper;
import com.group.exam.exam.mapper.PaperMapper;
import com.group.exam.exam.mapper.PaperQuestionMapper;
import com.group.exam.exam.support.QuesSnapshots;
import com.group.exam.exam.support.RandomStrategy;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.DifficultyEnum;
import com.group.exam.question.enums.QuesTypeEnum;
import com.group.exam.question.service.QuesItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 试卷管理（PAPER-01~10）
 *
 * <p>说明：t_paper 无组织字段，试卷与题库同为全集团共享，不做组织数据权限过滤。
 */
@Service
public class PaperService extends ServiceImpl<PaperMapper, Paper> {

    private final PaperQuestionMapper paperQuestionMapper;
    private final ExamSessionMapper examSessionMapper;
    private final QuesItemService quesItemService;
    private final GradeRuleService gradeRuleService;

    public PaperService(PaperQuestionMapper paperQuestionMapper, ExamSessionMapper examSessionMapper,
                        QuesItemService quesItemService, GradeRuleService gradeRuleService) {
        this.paperQuestionMapper = paperQuestionMapper;
        this.examSessionMapper = examSessionMapper;
        this.quesItemService = quesItemService;
        this.gradeRuleService = gradeRuleService;
    }

    // ---------------------------------------------------------------- PAPER-01 分页查询

    public PageResult<Map<String, Object>> page(String keyword, String paperType, String status,
                                                long pageNum, long pageSize) {
        QueryWrapper<Paper> qw = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like("paper_name", keyword.trim());
        }
        if (StringUtils.hasText(paperType)) {
            qw.eq("paper_type", parsePaperType(paperType));
        }
        PaperStatusEnum st = PaperStatusEnum.parseOrNull(status);
        qw.eq(st != null, "paper_status", st == null ? null : st.getCode());
        qw.orderByDesc("id");
        Page<Paper> page = page(new Page<>(pageNum, pageSize), qw);

        List<Long> paperIds = page.getRecords().stream().map(Paper::getId).toList();
        Map<Long, Long> useCounts = countUsage(paperIds);
        Map<Long, Long> quesCounts = countQuestions(paperIds);
        return PageResult.of(page).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("paperId", p.getId());
            m.put("paperName", p.getPaperName());
            m.put("paperType", p.getPaperType() != null && p.getPaperType() == 2 ? "RANDOM" : "FIXED");
            m.put("totalScore", p.getTotalScore());
            m.put("passScore", p.getPassScore());
            m.put("quesCount", p.getPaperType() != null && p.getPaperType() == 2
                    ? strategyQuesCount(p.getRandomStrategy()) : quesCounts.getOrDefault(p.getId(), 0L));
            m.put("status", PaperStatusEnum.toApi(p.getPaperStatus()));
            m.put("useCount", useCounts.getOrDefault(p.getId(), 0L));
            m.put("createdBy", p.getCreateBy());
            m.put("createdTime", p.getCreateTime());
            return m;
        });
    }

    // ---------------------------------------------------------------- PAPER-02 创建

    @Transactional(rollbackFor = Exception.class)
    public Long create(PaperSaveReq req) {
        if (!StringUtils.hasText(req.getPaperName())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "试卷名称不能为空");
        }
        int paperType = parsePaperType(req.getPaperType());
        BigDecimal totalScore = req.getTotalScore() == null ? new BigDecimal("100") : req.getTotalScore();
        BigDecimal passScore = req.getPassScore() == null ? new BigDecimal("60") : req.getPassScore();
        MultiScoreRuleEnum rule = MultiScoreRuleEnum.parseOrNull(req.getMultiScoreRule());
        int multiJudgeRule = rule == null ? MultiScoreRuleEnum.HALF_ON_MISS.getCode() : rule.getCode();

        Paper paper = new Paper();
        paper.setPaperName(req.getPaperName().trim());
        paper.setPaperType(paperType);
        paper.setTotalScore(totalScore);
        paper.setPassScore(passScore);
        paper.setMultiJudgeRule(multiJudgeRule);
        paper.setVersion(1);
        paper.setCreateBy(SecurityUtils.getUserId());

        if (paperType == 1) {
            // 固定卷：可带题目创建；不带题目则为草稿
            if (req.getQuestions() != null && !req.getQuestions().isEmpty()) {
                paper.setPaperStatus(PaperStatusEnum.READY.getCode());
            } else {
                paper.setPaperStatus(PaperStatusEnum.EDITING.getCode());
            }
        } else {
            // 随机卷：解析策略并校验可用题量与卷面总分
            List<RandomStrategy.Rule> rules = RandomStrategy.parse(req.getRandomStrategy());
            validateRandomStrategy(rules, totalScore);
            paper.setRandomStrategy(req.getRandomStrategy());
            paper.setSamePaper(Boolean.TRUE.equals(req.getSamePaperForAll()) ? 1 : 0);
            paper.setPaperStatus(PaperStatusEnum.READY.getCode());
        }

        // 等级规则：自定义则校验并新建；否则绑定系统默认规则
        if (req.getGradeRules() != null && !req.getGradeRules().isEmpty()) {
            paper.setGradeRuleId(gradeRuleService.createRule(
                    paper.getPaperName() + " 等级规则", totalScore, req.getGradeRules()));
        } else {
            paper.setGradeRuleId(gradeRuleService.defaultRuleId());
        }
        save(paper);

        if (paperType == 1 && req.getQuestions() != null && !req.getQuestions().isEmpty()) {
            replaceQuestions(paper, req.getQuestions());
        }
        return paper.getId();
    }

    // ---------------------------------------------------------------- PAPER-03 详情

    public Map<String, Object> detail(Long paperId) {
        Paper paper = requirePaper(paperId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paperId", paper.getId());
        m.put("paperName", paper.getPaperName());
        m.put("paperType", paper.getPaperType() == 2 ? "RANDOM" : "FIXED");
        m.put("totalScore", paper.getTotalScore());
        m.put("passScore", paper.getPassScore());
        m.put("multiScoreRule", MultiScoreRuleEnum.toApi(paper.getMultiJudgeRule()));
        m.put("samePaperForAll", paper.getSamePaper() != null && paper.getSamePaper() == 1);
        m.put("randomStrategy", paper.getRandomStrategy());
        m.put("status", PaperStatusEnum.toApi(paper.getPaperStatus()));
        m.put("version", paper.getVersion());
        m.put("gradeRuleId", paper.getGradeRuleId());
        m.put("gradeRules", gradeRuleService.snapshotItems(paper.getGradeRuleId()));
        m.put("createdBy", paper.getCreateBy());
        m.put("createdTime", paper.getCreateTime());
        m.put("sealedTime", paper.getPaperStatus() != null && paper.getPaperStatus() == 2 ? paper.getUpdateTime() : null);
        List<Long> usedBy = examSessionMapper.selectList(new QueryWrapper<ExamSession>()
                        .select("id").eq("paper_id", paperId).orderByDesc("id"))
                .stream().map(ExamSession::getId).toList();
        m.put("usedByExamIds", usedBy);
        if (paper.getPaperType() == 1) {
            List<Map<String, Object>> questions = new ArrayList<>();
            for (PaperQuestion pq : listQuestions(paperId)) {
                Map<String, Object> q = new LinkedHashMap<>(pq.getQuesSnapshot() == null ? Map.of() : pq.getQuesSnapshot());
                q.put("itemId", pq.getQuesId());
                q.put("seq", pq.getSortNo());
                q.put("score", pq.getScore());
                q.put("quesType", QuesTypeEnum.toApi(pq.getQuesType()));
                questions.add(q);
            }
            m.put("questions", questions);
        }
        return m;
    }

    // ---------------------------------------------------------------- PAPER-04 编辑基础信息与策略

    /**
     * 编辑试卷：未封存（SEALED）且未被考试场次引用时可修改基础信息、抽题策略与分值设计。
     * 随机卷修改 randomStrategy/totalScore 时按创建口径重新校验（题量充足 + 策略分值合计=卷面总分）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long paperId, PaperUpdateReq req) {
        Paper paper = requirePaper(paperId);
        requireNotSealed(paper);
        requireNotReferenced(paper);
        if (StringUtils.hasText(req.getPaperName())) {
            paper.setPaperName(req.getPaperName().trim());
        }
        if (req.getTotalScore() != null) {
            paper.setTotalScore(req.getTotalScore());
        }
        if (req.getPassScore() != null) {
            paper.setPassScore(req.getPassScore());
        }
        MultiScoreRuleEnum rule = MultiScoreRuleEnum.parseOrNull(req.getMultiScoreRule());
        if (rule != null) {
            paper.setMultiJudgeRule(rule.getCode());
        }
        if (req.getSamePaperForAll() != null) {
            if (paper.getPaperType() != 2) {
                throw new BizException(ErrorCode.PARAM_ERROR, "仅随机卷支持 samePaperForAll");
            }
            paper.setSamePaper(Boolean.TRUE.equals(req.getSamePaperForAll()) ? 1 : 0);
        }
        if (paper.getPaperType() == 2) {
            // 随机卷：策略全量替换或仅改总分时，均需满足 策略分值合计=卷面总分
            List<RandomStrategy.Rule> rules = req.getRandomStrategy() != null
                    ? RandomStrategy.parse(req.getRandomStrategy())
                    : RandomStrategy.parse(paper.getRandomStrategy());
            try {
                validateRandomStrategy(rules, paper.getTotalScore());
            } catch (BizException e) {
                if (ErrorCode.PAPER_SCORE_MISMATCH.getCode() == e.getCode() && req.getRandomStrategy() == null) {
                    throw new BizException(ErrorCode.PAPER_SCORE_MISMATCH,
                            "卷面总分与抽题策略分值合计不一致，请同步调整 randomStrategy 后再修改");
                }
                throw e;
            }
            if (req.getRandomStrategy() != null) {
                paper.setRandomStrategy(req.getRandomStrategy());
            }
        } else if (req.getRandomStrategy() != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅随机卷支持修改 randomStrategy");
        }
        paper.setUpdateBy(SecurityUtils.getUserId());
        updateById(paper);
    }

    /** 校验随机策略：可用题量充足（40005）且策略分值合计等于卷面总分（40003） */
    private void validateRandomStrategy(List<RandomStrategy.Rule> rules, BigDecimal totalScore) {
        for (RandomStrategy.Rule r : rules) {
            long available = quesItemService.countEnabled(r.bankIds(), r.includeChildren(), r.quesType(), r.difficulty());
            if (available < r.count()) {
                throw new BizException(ErrorCode.RANDOM_STRATEGY_INSUFFICIENT,
                        "抽题策略可用题量不足：" + ruleDesc(r) + " 需" + r.count() + "道，候选仅" + available + "道");
            }
        }
        BigDecimal sum = rules.stream().map(r -> r.score().multiply(BigDecimal.valueOf(r.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalScore) != 0) {
            throw new BizException(ErrorCode.PAPER_SCORE_MISMATCH,
                    "策略分值合计 " + sum + " 与卷面总分 " + totalScore + " 不一致");
        }
    }

    /** 被考试场次引用（含进行中/已结束）的试卷不可编辑，须先封存口径保护已产生的答卷快照 */
    private void requireNotReferenced(Paper paper) {
        Long refs = examSessionMapper.selectCount(new QueryWrapper<ExamSession>().eq("paper_id", paper.getId()));
        if (refs != null && refs > 0) {
            throw new BizException(ErrorCode.PAPER_SEALED, "试卷已被考试引用，不可编辑");
        }
    }

    // ---------------------------------------------------------------- PAPER-05 删除

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long paperId) {
        Paper paper = requirePaper(paperId);
        Long refs = examSessionMapper.selectCount(new QueryWrapper<ExamSession>().eq("paper_id", paperId));
        if (refs != null && refs > 0) {
            throw new BizException(ErrorCode.PAPER_SEALED, "试卷已被考试引用，不可删除");
        }
        removeById(paperId);
        paperQuestionMapper.physicalDeleteByPaperId(paperId);
    }

    // ---------------------------------------------------------------- PAPER-06 固定卷选题（全量替换）

    @Transactional(rollbackFor = Exception.class)
    public void setQuestions(Long paperId, PaperQuestionsReq req) {
        Paper paper = requirePaper(paperId);
        requireNotSealed(paper);
        requireNotReferenced(paper);
        if (paper.getPaperType() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅固定卷支持选题设置");
        }
        if (req.getQuestions() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "questions 不能为空");
        }
        replaceQuestions(paper, req.getQuestions());
        // 选题完整后试卷进入启用态
        if (!req.getQuestions().isEmpty() && paper.getPaperStatus() == PaperStatusEnum.EDITING.getCode()) {
            Paper update = new Paper();
            update.setId(paper.getId());
            update.setPaperStatus(PaperStatusEnum.READY.getCode());
            update.setUpdateBy(SecurityUtils.getUserId());
            updateById(update);
        }
    }

    /** 全量替换 t_paper_question：校验题目启用（40007）与总分一致（40003），逐题写快照 */
    private void replaceQuestions(Paper paper, List<PaperSaveReq.QuestionItem> questions) {
        Set<Long> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        List<PaperQuestion> rows = new ArrayList<>();
        int seqFallback = 0;
        for (PaperSaveReq.QuestionItem q : questions) {
            if (q.getItemId() == null || q.getScore() == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "选题项须含 itemId 与 score");
            }
            if (!seen.add(q.getItemId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "题目重复选择: " + q.getItemId());
            }
            QuesItem item = quesItemService.getEnabledItem(q.getItemId());
            if (item == null) {
                throw new BizException(ErrorCode.QUESTION_NOT_AUDITED, "题目未审核启用，不可用于组卷: " + q.getItemId());
            }
            sum = sum.add(q.getScore());
            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuesId(item.getId());
            pq.setQuesType(item.getQuesType());
            pq.setScore(q.getScore());
            pq.setSortNo(q.getSeq() != null ? q.getSeq() : ++seqFallback);
            pq.setQuesSnapshot(QuesSnapshots.of(item));
            pq.setCreateBy(SecurityUtils.getUserId());
            rows.add(pq);
        }
        if (sum.compareTo(paper.getTotalScore()) != 0) {
            throw new BizException(ErrorCode.PAPER_SCORE_MISMATCH,
                    "选题分值合计 " + sum + " 与卷面总分 " + paper.getTotalScore() + " 不一致");
        }
        paperQuestionMapper.physicalDeleteByPaperId(paper.getId());
        for (PaperQuestion row : rows) {
            paperQuestionMapper.insert(row);
        }
    }

    // ---------------------------------------------------------------- PAPER-07 预览（含答案解析）

    public Map<String, Object> preview(Long paperId) {
        Paper paper = requirePaper(paperId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paperName", paper.getPaperName());
        m.put("totalScore", paper.getTotalScore());
        m.put("passScore", paper.getPassScore());
        m.put("paperType", paper.getPaperType() == 2 ? "RANDOM" : "FIXED");

        List<Map<String, Object>> questions = new ArrayList<>();
        if (paper.getPaperType() == 1) {
            for (PaperQuestion pq : listQuestions(paperId)) {
                Map<String, Object> q = new LinkedHashMap<>(pq.getQuesSnapshot() == null ? Map.of() : pq.getQuesSnapshot());
                q.put("seq", pq.getSortNo());
                q.put("itemId", pq.getQuesId());
                q.put("score", pq.getScore());
                q.put("quesTypeCode", pq.getQuesType());
                questions.add(q);
            }
            m.put("sample", false);
        } else {
            // 随机卷：按策略试抽一份样卷（不落库），标注 sample
            List<RandomStrategy.Rule> rules = RandomStrategy.parse(paper.getRandomStrategy());
            Set<Long> drawn = new HashSet<>();
            int seq = 0;
            for (RandomStrategy.Rule r : rules) {
                List<QuesItem> items = quesItemService.drawRandom(
                        r.bankIds(), r.includeChildren(), r.quesType(), r.difficulty(), r.count());
                if (items.size() < r.count()) {
                    throw new BizException(ErrorCode.RANDOM_STRATEGY_INSUFFICIENT,
                            "抽题策略可用题量不足：" + ruleDesc(r) + " 需" + r.count() + "道，候选仅" + items.size() + "道");
                }
                for (QuesItem item : items) {
                    if (!drawn.add(item.getId())) {
                        continue;
                    }
                    Map<String, Object> q = QuesSnapshots.of(item);
                    q.put("seq", ++seq);
                    q.put("itemId", item.getId());
                    q.put("score", r.score());
                    q.put("quesTypeCode", item.getQuesType());
                    questions.add(q);
                }
            }
            m.put("sample", true);
        }
        m.put("sections", toSections(questions));
        return m;
    }

    // ---------------------------------------------------------------- PAPER-08 复制

    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long paperId, PaperCopyReq req) {
        Paper source = requirePaper(paperId);
        Paper copy = new Paper();
        copy.setPaperName(StringUtils.hasText(req.getPaperName())
                ? req.getPaperName().trim() : source.getPaperName() + " 副本");
        copy.setPaperType(source.getPaperType());
        copy.setTotalScore(source.getTotalScore());
        copy.setPassScore(source.getPassScore());
        copy.setMultiJudgeRule(source.getMultiJudgeRule());
        copy.setRandomStrategy(source.getRandomStrategy());
        copy.setSamePaper(source.getSamePaper());
        copy.setGradeRuleId(source.getGradeRuleId());
        copy.setPaperStatus(PaperStatusEnum.EDITING.getCode());
        copy.setVersion((source.getVersion() == null ? 1 : source.getVersion()) + 1);
        copy.setCreateBy(SecurityUtils.getUserId());
        save(copy);
        for (PaperQuestion pq : listQuestions(paperId)) {
            PaperQuestion row = new PaperQuestion();
            row.setPaperId(copy.getId());
            row.setQuesId(pq.getQuesId());
            row.setQuesType(pq.getQuesType());
            row.setScore(pq.getScore());
            row.setSortNo(pq.getSortNo());
            row.setQuesSnapshot(pq.getQuesSnapshot());
            row.setCreateBy(copy.getCreateBy());
            paperQuestionMapper.insert(row);
        }
        return copy.getId();
    }

    // ---------------------------------------------------------------- PAPER-09 封存（幂等）

    @Transactional(rollbackFor = Exception.class)
    public void seal(Long paperId) {
        Paper paper = requirePaper(paperId);
        if (paper.getPaperStatus() == PaperStatusEnum.SEALED.getCode()) {
            return;
        }
        paper.setPaperStatus(PaperStatusEnum.SEALED.getCode());
        paper.setUpdateBy(SecurityUtils.getUserId());
        updateById(paper);
    }

    /** 场次引用试卷时自动封存（启用态才流转，幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void sealOnReferenced(Long paperId) {
        Paper paper = getById(paperId);
        if (paper != null && paper.getPaperStatus() == PaperStatusEnum.READY.getCode()) {
            paper.setPaperStatus(PaperStatusEnum.SEALED.getCode());
            updateById(paper);
        }
    }

    // ---------------------------------------------------------------- PAPER-10 配置等级规则

    @Transactional(rollbackFor = Exception.class)
    public void saveGradeRules(Long paperId, List<GradeRuleItemReq> gradeRules) {
        Paper paper = requirePaper(paperId);
        requireNotSealed(paper);
        Long ruleId = gradeRuleService.createRule(paper.getPaperName() + " 等级规则", paper.getTotalScore(), gradeRules);
        Paper update = new Paper();
        update.setId(paper.getId());
        update.setGradeRuleId(ruleId);
        update.setUpdateBy(SecurityUtils.getUserId());
        updateById(update);
    }

    // ---------------------------------------------------------------- 内部工具

    public Paper requirePaper(Long paperId) {
        Paper paper = getById(paperId);
        if (paper == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "试卷不存在: " + paperId);
        }
        return paper;
    }

    private void requireNotSealed(Paper paper) {
        if (paper.getPaperStatus() != null && paper.getPaperStatus() == PaperStatusEnum.SEALED.getCode()) {
            throw new BizException(ErrorCode.PAPER_SEALED);
        }
    }

    public List<PaperQuestion> listQuestions(Long paperId) {
        return paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>()
                .eq("paper_id", paperId).orderByAsc("sort_no"));
    }

    private Map<Long, Long> countUsage(List<Long> paperIds) {
        if (paperIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = examSessionMapper.selectMaps(new QueryWrapper<ExamSession>()
                .select("paper_id", "COUNT(*) AS cnt")
                .in("paper_id", paperIds)
                .groupBy("paper_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("paper_id")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    private Map<Long, Long> countQuestions(List<Long> paperIds) {
        if (paperIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = paperQuestionMapper.selectMaps(new QueryWrapper<PaperQuestion>()
                .select("paper_id", "COUNT(*) AS cnt")
                .in("paper_id", paperIds)
                .groupBy("paper_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("paper_id")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    private static long strategyQuesCount(List<Map<String, Object>> strategy) {
        if (strategy == null) {
            return 0;
        }
        return strategy.stream().mapToLong(r -> RandomStrategy.asInt(r.get("count"), 0)).sum();
    }

    static String ruleDesc(RandomStrategy.Rule r) {
        return QuesTypeEnum.toApi(r.quesType()) + "/" +
                (r.difficulty() == null ? "不限难度" : DifficultyEnum.toApi(r.difficulty()));
    }

    /** 题目列表按题型分组为 sections：[{quesType, typeName, scorePerQues, questions}] */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> toSections(List<Map<String, Object>> questions) {
        Map<Integer, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> q : questions) {
            Integer type = (Integer) q.get("quesTypeCode");
            grouped.computeIfAbsent(type == null ? 0 : type, k -> new ArrayList<>()).add(q);
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        grouped.forEach((type, list) -> {
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("quesType", QuesTypeEnum.toApi(type));
            QuesTypeEnum e = QuesTypeEnum.ofCode(type);
            section.put("typeName", e == null ? "其他" : e.getCnName() + "题");
            section.put("scorePerQues", list.isEmpty() ? null : list.get(0).get("score"));
            List<Map<String, Object>> qs = new ArrayList<>();
            for (Map<String, Object> q : list) {
                Map<String, Object> item = new LinkedHashMap<>(q);
                item.remove("quesTypeCode");
                qs.add(item);
            }
            section.put("questions", qs);
            sections.add(section);
        });
        return sections;
    }

    private static int parsePaperType(String paperType) {
        if (!StringUtils.hasText(paperType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "试卷类型不能为空（FIXED/RANDOM）");
        }
        return switch (paperType.trim().toUpperCase()) {
            case "FIXED" -> 1;
            case "RANDOM" -> 2;
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法试卷类型: " + paperType);
        };
    }
}
