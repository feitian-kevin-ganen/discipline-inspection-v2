package com.group.exam.server.aigen.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.common.util.JsonUtils;
import com.group.exam.learn.entity.LearnMaterial;
import com.group.exam.learn.service.LearnCategoryService;
import com.group.exam.learn.service.LearnMaterialService;
import com.group.exam.question.dto.BankSaveReq;
import com.group.exam.question.dto.ItemSaveReq;
import com.group.exam.question.entity.QuesBank;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.DifficultyEnum;
import com.group.exam.question.enums.QuesTypeEnum;
import com.group.exam.question.service.QuesBankService;
import com.group.exam.question.service.QuesItemService;
import com.group.exam.question.support.StemTextUtils;
import com.group.exam.server.aigen.dto.QuestionUpdateReq;
import com.group.exam.server.aigen.dto.TaskCreateReq;
import com.group.exam.server.aigen.entity.AiGenQuestion;
import com.group.exam.server.aigen.entity.AiGenTask;
import com.group.exam.server.aigen.mapper.AiGenQuestionMapper;
import com.group.exam.server.aigen.mapper.AiGenTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * AI 辅助生题服务：任务创建（异步 mock 生成）、任务/题目查询、编辑、采纳（自动去重入库）、丢弃。
 * 采纳入库复用 QuesItemService.create 的校验链路；去重按规范化 stem_md5 精确命中。
 */
@Service
public class AiGenService {

    /** mock 生成的基础延迟 + 每题附加延迟，模拟大模型耗时 */
    private static final long MOCK_BASE_DELAY_MS = 3000;
    private static final long MOCK_PER_QUES_MS = 400;

    /** AI 生成题目统一挂载的根题库 */
    /** AI 采纳题目的目标根题库（其下按资料标题 find-or-create 子题库） */
    private static final String TARGET_ROOT_BANK = "纪检监察题库-正式";

    /** model=真实大模型（国联模型平台）；mock=本地模拟生成（无 key 环境/演示用） */
    @org.springframework.beans.factory.annotation.Value("${exam.ai-gen.mode:model}")
    private String genMode;

    private final AiGenTaskMapper taskMapper;
    private final AiGenQuestionMapper questionMapper;
    private final LearnMaterialService learnMaterialService;
    private final LearnCategoryService learnCategoryService;
    private final QuesBankService quesBankService;
    private final QuesItemService quesItemService;
    private final GliaiModelClient modelClient;
    private final MaterialTextExtractor materialTextExtractor;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ai-gen-worker");
        t.setDaemon(true);
        return t;
    });

    public AiGenService(AiGenTaskMapper taskMapper, AiGenQuestionMapper questionMapper,
                        LearnMaterialService learnMaterialService, LearnCategoryService learnCategoryService,
                        QuesBankService quesBankService, QuesItemService quesItemService,
                        GliaiModelClient modelClient, MaterialTextExtractor materialTextExtractor) {
        this.taskMapper = taskMapper;
        this.questionMapper = questionMapper;
        this.learnMaterialService = learnMaterialService;
        this.learnCategoryService = learnCategoryService;
        this.quesBankService = quesBankService;
        this.quesItemService = quesItemService;
        this.modelClient = modelClient;
        this.materialTextExtractor = materialTextExtractor;
    }

    /** 已发布学习资料列表（AI 生题选择器），可按资料分类（含下级）过滤 */
    public List<Map<String, Object>> materials(Long categoryId) {
        QueryWrapper<LearnMaterial> qw = new QueryWrapper<LearnMaterial>()
                .select("id", "title", "file_type", "audit_status")
                .eq("audit_status", 2)
                // 所属分类被删除（标记删除）的资料不再出现在生题选择器
                .inSql("category_id", "SELECT id FROM t_learn_category WHERE deleted = 0")
                .orderByAsc("sort_no").orderByDesc("id");
        if (categoryId != null) {
            List<Long> catIds = learnCategoryService.collectSubIds(categoryId);
            if (catIds.isEmpty()) {
                return List.of();
            }
            qw.in("category_id", catIds);
        }
        List<LearnMaterial> list = learnMaterialService.list(qw);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnMaterial m : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialId", m.getId());
            item.put("title", m.getTitle());
            item.put("materialType", com.group.exam.learn.service.LearnMaterialService.materialTypeOf(m.getFileType()));
            out.add(item);
        }
        return out;
    }

    /** 创建生成任务：每个资料一个任务，立即返回任务列表，生成异步执行 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createTasks(TaskCreateReq req) {
        if (req.getMaterialIds() == null || req.getMaterialIds().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请至少选择一个学习资料");
        }
        Map<String, Integer> typeCounts = normalizeTypeCounts(req.getTypeCounts());
        int total = typeCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请设置题型数量（总量须大于 0）");
        }
        if (total > 50) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单次生成题目总数不能超过 50");
        }
        List<String> difficulties = expandDifficulties(req.getDifficultyCounts(), total);

        List<Long> taskIds = new ArrayList<>();
        for (Long materialId : req.getMaterialIds()) {
            LearnMaterial m = learnMaterialService.getById(materialId);
            if (m == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "学习资料不存在: " + materialId);
            }
            Long bankId = findOrCreateMaterialBank(m.getTitle());
            AiGenTask task = new AiGenTask();
            task.setMaterialId(m.getId());
            task.setMaterialTitle(m.getTitle());
            task.setBankId(bankId);
            task.setStatus("RUNNING");
            task.setQuesCount(0);
            task.setStartTime(LocalDateTime.now());
            task.setCreateBy(currentUserIdOrNull());
            task.setCreateTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.insert(task);
            taskIds.add(task.getId());
            Long tid = task.getId();
            Map<String, Integer> countsCopy = Map.copyOf(typeCounts);
            List<String> diffCopy = List.copyOf(difficulties);
            // 事务提交后再触发异步生成：避免工作线程读不到未提交的任务行
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            executor.submit(() -> runTask(tid, countsCopy, diffCopy));
                        }
                    });
        }
        return taskIds;
    }

    /** 任务列表（倒序）：含生成时长（秒） */
    public List<Map<String, Object>> listTasks() {
        List<AiGenTask> tasks = taskMapper.selectList(new QueryWrapper<AiGenTask>().orderByDesc("id"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiGenTask t : tasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", t.getId());
            item.put("materialTitle", t.getMaterialTitle());
            item.put("status", t.getStatus());
            item.put("quesCount", t.getQuesCount());
            item.put("typeSummary", t.getTypeSummary());
            item.put("errorMsg", t.getErrorMsg());
            item.put("startTime", t.getStartTime());
            item.put("endTime", t.getEndTime());
            Long durationSec = null;
            if (t.getStartTime() != null && t.getEndTime() != null) {
                durationSec = Duration.between(t.getStartTime(), t.getEndTime()).getSeconds();
            }
            item.put("durationSec", durationSec);
            out.add(item);
        }
        return out;
    }

    public List<AiGenQuestion> listQuestions(Long taskId) {
        return questionMapper.selectList(new QueryWrapper<AiGenQuestion>()
                .eq("task_id", taskId).orderByAsc("id"));
    }

    /** 删除任务及其下全部生成题目（逻辑删除，可回滚）；生成中的任务不可删除 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long taskId) {
        AiGenTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "生成任务不存在");
        }
        if ("RUNNING".equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务正在生成中，不能删除");
        }
        questionMapper.delete(new QueryWrapper<AiGenQuestion>().eq("task_id", taskId));
        taskMapper.deleteById(taskId);
    }

    /** 采纳前编辑：仅 PENDING 状态可编辑 */
    public void updateQuestion(Long questionId, QuestionUpdateReq req) {
        AiGenQuestion q = requireQuestion(questionId);
        if (!"PENDING".equals(q.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该题目已" + ("ADOPTED".equals(q.getStatus()) ? "采纳" : "丢弃") + "，不可编辑");
        }
        if (req.getStem() != null) {
            if (req.getStem().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "题干不能为空");
            }
            q.setStem(req.getStem().trim());
        }
        if (req.getOptions() != null) {
            q.setOptionsJson(JsonUtils.toJson(req.getOptions()));
        }
        if (req.getAnswer() != null) {
            q.setAnswerJson(JsonUtils.toJson(req.getAnswer()));
        }
        if (req.getAnalysis() != null) {
            q.setAnalysis(req.getAnalysis());
        }
        if (req.getDifficulty() != null && !req.getDifficulty().isBlank()) {
            DifficultyEnum.parse(req.getDifficulty());
            q.setDifficulty(req.getDifficulty().trim());
        }
        q.setUpdateTime(LocalDateTime.now());
        questionMapper.updateById(q);
    }

    /**
     * 采纳：进入任务对应资料题库，自动去重（规范化 stem_md5 精确命中已存在题目则拒绝）。
     * 入库复用 QuesItemService.create（含题型结构校验与查重提示）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long adopt(Long questionId) {
        AiGenQuestion q = requireQuestion(questionId);
        if (!"PENDING".equals(q.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该题目已" + ("ADOPTED".equals(q.getStatus()) ? "采纳" : "丢弃") + "，不可重复操作");
        }
        AiGenTask task = taskMapper.selectById(q.getTaskId());
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "生成任务不存在");
        }
        // 采纳目标题库实时解析（按资料标题落到「纪检监察题库-正式」对应文件题库），
        // 不依赖任务创建时存的 bankId，兼容历史任务（原指向已下线的 AI生成题库）
        Long targetBankId = findOrCreateMaterialBank(task.getMaterialTitle());
        // 自动去重：与现有正式题目（含其他已采纳题）按题干 MD5 精确比对
        String md5 = StemTextUtils.normalizeMd5(q.getStem());
        Long dup = quesItemService.lambdaQuery()
                .eq(QuesItem::getStemMd5, md5)
                .select(QuesItem::getId)
                .list().stream().findFirst().map(QuesItem::getId).orElse(null);
        if (dup != null) {
            throw new BizException(ErrorCode.QUESTION_DUPLICATED, "该题与题库中现有题目（id=" + dup + "）重复，未采纳");
        }

        ItemSaveReq req = new ItemSaveReq();
        req.setBankId(targetBankId);
        req.setQuesType(q.getQuesType());
        req.setDifficulty(q.getDifficulty());
        req.setStem(q.getStem());
        req.setAnalysis(q.getAnalysis());
        req.setGenType("AI");
        req.setSourceLaw(task.getMaterialTitle());
        if (q.getOptionsJson() != null && !q.getOptionsJson().isBlank()) {
            List<Map<String, Object>> opts = JsonUtils.fromJson(q.getOptionsJson(), List.class);
            List<ItemSaveReq.OptionReq> optionReqs = new ArrayList<>();
            for (Map<String, Object> o : opts) {
                ItemSaveReq.OptionReq or = new ItemSaveReq.OptionReq();
                or.setOptKey(String.valueOf(o.get("optKey")));
                or.setContent(String.valueOf(o.getOrDefault("content", "")));
                or.setIsAnswer(Boolean.TRUE.equals(o.get("isAnswer")));
                optionReqs.add(or);
            }
            req.setOptions(optionReqs);
        }
        if (q.getAnswerJson() != null && !q.getAnswerJson().isBlank()) {
            req.setAnswer(JsonUtils.fromJson(q.getAnswerJson(), Map.class));
        }
        Long itemId = quesItemService.create(req).getItemId();

        q.setStatus("ADOPTED");
        q.setAdoptItemId(itemId);
        q.setUpdateTime(LocalDateTime.now());
        questionMapper.updateById(q);
        return itemId;
    }

    /** 丢弃：不再进入题库 */
    public void discard(Long questionId) {
        AiGenQuestion q = requireQuestion(questionId);
        if (!"PENDING".equals(q.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该题目已" + ("ADOPTED".equals(q.getStatus()) ? "采纳" : "丢弃") + "，不可重复操作");
        }
        q.setStatus("DISCARDED");
        q.setUpdateTime(LocalDateTime.now());
        questionMapper.updateById(q);
    }

    // ---------------- 内部 ----------------

    private void runTask(Long taskId, Map<String, Integer> typeCounts, List<String> difficulties) {
        AiGenTask task = null;
        for (int i = 0; i < 5 && task == null; i++) {
            task = taskMapper.selectById(taskId);
            if (task == null) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        if (task == null) {
            return;
        }
        try {
            int total = typeCounts.values().stream().mapToInt(Integer::intValue).sum();
            List<AiGenQuestion> generated;
            if ("mock".equalsIgnoreCase(genMode)) {
                // mock 模式：本地模拟生成（无模型 key 的环境/演示）
                Thread.sleep(MOCK_BASE_DELAY_MS + total * MOCK_PER_QUES_MS);
                generated = MockAiGenerator.generate(task.getMaterialTitle(), typeCounts, difficulties);
            } else {
                // 真实模型：国联模型平台（OpenAI 兼容）；正文提取含 PDF/docx 文本与扫描版 OCR
                List<String> existingStems = loadExistingStems();
                LearnMaterial material = learnMaterialService.getById(task.getMaterialId());
                String materialText = material == null ? null : materialTextExtractor.extract(material);
                generated = modelClient.generate(task.getMaterialTitle(), materialText,
                        typeCounts, difficulties, existingStems);
            }
            // 生成期去重：
            // 1) 与现有正式题目（含本资料已采纳/用户手工导入的题目）按规范化 stem_md5 比对，剔除重复；
            // 2) 生成结果批内自去重。避免生成出注定无法采纳的题目。
            // 接入真实模型时，应把已有题干清单一并传给模型，从源头避免重复生成。
            Set<String> existingMd5 = quesItemService.lambdaQuery()
                    .select(QuesItem::getStemMd5)
                    .list().stream()
                    .map(QuesItem::getStemMd5)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> seenMd5 = new java.util.HashSet<>();
            List<AiGenQuestion> kept = new ArrayList<>();
            int dupRemoved = 0;
            for (AiGenQuestion q : generated) {
                String md5 = StemTextUtils.normalizeMd5(q.getStem());
                if (existingMd5.contains(md5) || !seenMd5.add(md5)) {
                    dupRemoved++;
                    continue;
                }
                kept.add(q);
            }
            LocalDateTime now = LocalDateTime.now();
            for (AiGenQuestion q : kept) {
                q.setTaskId(taskId);
                q.setCreateTime(now);
                q.setUpdateTime(now);
                questionMapper.insert(q);
            }
            task.setStatus("SUCCESS");
            task.setQuesCount(kept.size());
            task.setTypeSummary(buildSummary(kept, dupRemoved));
            task.setEndTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            task.setEndTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    /** 已有题目题干清单（传给模型避免重复生成，最多取最近 50 条） */
    private List<String> loadExistingStems() {
        return quesItemService.lambdaQuery()
                .select(QuesItem::getStem)
                .orderByDesc(QuesItem::getId)
                .last("LIMIT 50")
                .list().stream()
                .map(QuesItem::getStem)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String buildSummary(List<AiGenQuestion> generated, int dupRemoved) {        if (generated.isEmpty()) {
            return "全部与现有题目重复，已去重剔除 " + dupRemoved + " 题";
        }
        Map<String, Long> byType = generated.stream().collect(Collectors.groupingBy(
                AiGenQuestion::getQuesType, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byDiff = generated.stream().collect(Collectors.groupingBy(
                q -> q.getDifficulty() == null ? "MEDIUM" : q.getDifficulty(), LinkedHashMap::new, Collectors.counting()));
        StringBuilder sb = new StringBuilder();
        byType.forEach((k, v) -> sb.append(QuesTypeEnum.parse(k).getCnName()).append(":").append(v).append("；"));
        sb.append("｜");
        byDiff.forEach((k, v) -> sb.append(DifficultyEnum.parse(k).getCnName()).append(":").append(v).append("；"));
        if (dupRemoved > 0) {
            sb.append("｜已去重剔除 ").append(dupRemoved).append(" 题");
        }
        return sb.toString();
    }

    private Map<String, Integer> normalizeTypeCounts(Map<String, Integer> raw) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (QuesTypeEnum t : QuesTypeEnum.values()) {
            int n = raw == null ? 0 : raw.getOrDefault(t.getApiCode(), 0);
            out.put(t.getApiCode(), Math.max(0, Math.min(n, 50)));
        }
        return out;
    }

    /** 难度序列：按 EASY→MEDIUM→HARD 顺序展开指定数量；总量不足补 MEDIUM，超出截断 */
    private List<String> expandDifficulties(Map<String, Integer> difficultyCounts, int total) {
        List<String> seq = new ArrayList<>();
        if (difficultyCounts != null) {
            for (String key : List.of("EASY", "MEDIUM", "HARD")) {
                int n = Math.max(0, difficultyCounts.getOrDefault(key, 0));
                for (int i = 0; i < n; i++) {
                    seq.add(key);
                }
            }
        }
        while (seq.size() < total) {
            seq.add("MEDIUM");
        }
        return seq.size() > total ? seq.subList(0, total) : seq;
    }

    /**
     * 「纪检监察题库-正式」下按资料标题 find-or-create 题库（采纳后的入库位置）。
     * 匹配顺序：名称完全一致 → 互相包含（资料标题常带日期前缀，如「2016年10月27日中国共产党党内监督条例」
     * 命中正式子库「中国共产党党内监督条例」）→ 新建子库。
     */
    private Long findOrCreateMaterialBank(String materialTitle) {
        String title = materialTitle == null || materialTitle.isBlank() ? "未命名资料" : materialTitle.trim();
        if (title.length() > 120) {
            title = title.substring(0, 120);
        }
        QuesBank root = quesBankService.lambdaQuery()
                .isNull(QuesBank::getParentId)
                .eq(QuesBank::getBankName, TARGET_ROOT_BANK)
                .last("LIMIT 1")
                .one();
        Long rootId;
        if (root == null) {
            BankSaveReq rootReq = new BankSaveReq();
            rootReq.setBankName(TARGET_ROOT_BANK);
            rootReq.setParentId(0L);
            rootId = quesBankService.create(rootReq);
        } else {
            rootId = root.getId();
        }
        List<QuesBank> children = quesBankService.lambdaQuery()
                .eq(QuesBank::getParentId, rootId)
                .list();
        // 1) 名称完全一致
        for (QuesBank c : children) {
            if (c.getBankName() != null && c.getBankName().equals(title)) {
                return c.getId();
            }
        }
        // 2) 互相包含（忽略空白）：取名称最长者，避免误命中过短的子库
        String normTitle = title.replaceAll("\\s+", "");
        QuesBank best = null;
        for (QuesBank c : children) {
            if (c.getBankName() == null) {
                continue;
            }
            String normChild = c.getBankName().replaceAll("\\s+", "");
            if (normChild.length() >= 4 && (normTitle.contains(normChild) || normChild.contains(normTitle))) {
                if (best == null || normChild.length() > best.getBankName().replaceAll("\\s+", "").length()) {
                    best = c;
                }
            }
        }
        if (best != null) {
            return best.getId();
        }
        // 3) 新建子库
        BankSaveReq childReq = new BankSaveReq();
        childReq.setBankName(title);
        childReq.setParentId(rootId);
        return quesBankService.create(childReq);
    }

    private AiGenQuestion requireQuestion(Long questionId) {
        AiGenQuestion q = questionMapper.selectById(questionId);
        if (q == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "生成题目不存在: " + questionId);
        }
        return q;
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
