package com.group.exam.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.common.util.JsonUtils;
import com.group.exam.question.dto.DupHintVO;
import com.group.exam.question.dto.ItemCreateResp;
import com.group.exam.question.dto.ItemDetailVO;
import com.group.exam.question.dto.ItemPageVO;
import com.group.exam.question.dto.ItemSaveReq;
import com.group.exam.question.entity.QuesBank;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.AuditActionEnum;
import com.group.exam.question.enums.DifficultyEnum;
import com.group.exam.question.enums.ItemStatusEnum;
import com.group.exam.question.enums.QuesTypeEnum;
import com.group.exam.question.mapper.QuesItemMapper;
import com.group.exam.question.support.QuesItemAssembler;
import com.group.exam.question.support.StemTextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目 CRUD / 审核 / 分页查询（QUES-05~09、QUES-13），
 * 并向 exam-exam 模块暴露组卷/抽题查询能力。
 */
@Service
public class QuesItemService extends ServiceImpl<QuesItemMapper, QuesItem> {

    private final QuesBankService quesBankService;
    private final QuesDupCheckService quesDupCheckService;
    private final SysConfigService sysConfigService;

    public QuesItemService(QuesBankService quesBankService,
                           QuesDupCheckService quesDupCheckService,
                           SysConfigService sysConfigService) {
        this.quesBankService = quesBankService;
        this.quesDupCheckService = quesDupCheckService;
        this.sysConfigService = sysConfigService;
    }

    /**
     * 题目分页查询（QUES-05）：bankId 含下级，支持题型/难度/状态/题干关键字/标签/来源(genType: AI/MANUAL)
     */
    public PageResult<ItemPageVO> pageItems(Long bankId, String quesType, String difficulty, String status,
                                            String keyword, String tag, String genType, long pageNum, long pageSize) {
        QueryWrapper<QuesItem> qw = new QueryWrapper<>();
        if (bankId != null) {
            Set<Long> bankIds = quesBankService.expandBankIds(List.of(bankId), true);
            if (bankIds.isEmpty()) {
                return PageResult.of(0, List.of(), pageNum, pageSize);
            }
            qw.in("bank_id", bankIds);
        }
        QuesTypeEnum typeEnum = QuesTypeEnum.parseOrNull(quesType);
        if (quesType != null && !quesType.isBlank() && typeEnum == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "题型无法识别: " + quesType);
        }
        qw.eq(typeEnum != null, "ques_type", typeEnum == null ? null : typeEnum.getCode());
        DifficultyEnum diff = DifficultyEnum.parseOrNull(difficulty);
        if (difficulty != null && !difficulty.isBlank() && diff == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "难度无法识别: " + difficulty);
        }
        qw.eq(diff != null, "difficulty", diff == null ? null : diff.getCode());
        ItemStatusEnum st = ItemStatusEnum.parse(status);
        qw.eq(st != null, "status", st == null ? null : st.getCode());
        if (keyword != null && !keyword.isBlank()) {
            qw.like("stem", keyword.trim());
        }
        if (tag != null && !tag.isBlank()) {
            qw.apply("knowledge_tags @> CAST({0} AS jsonb)", JsonUtils.toJson(List.of(tag.trim())));
        }
        if (genType != null && !genType.isBlank()) {
            String gt = genType.trim().toUpperCase();
            if (!"AI".equals(gt) && !"MANUAL".equals(gt)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "题目来源无法识别: " + genType);
            }
            qw.eq("gen_type", gt);
        }
        qw.orderByDesc("id");

        Page<QuesItem> page = page(new Page<>(pageNum, pageSize), qw);
        Map<Long, String> bankNames = bankNamesOf(page.getRecords());
        return PageResult.of(page).map(item -> toPageVO(item, bankNames));
    }

    /**
     * 题目详情（QUES-07）
     */
    public ItemDetailVO detail(Long itemId) {
        QuesItem item = getById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在: " + itemId);
        }
        String bankName = bankNamesOf(List.of(item)).get(item.getBankId());
        return QuesItemAssembler.toDetailVO(item, bankName);
    }

    /**
     * 新增题目（QUES-06）：保存前自动查重，结果作为 dupHints 返回（不阻断；
     * 系统参数 ques.dup.block=true 时抛 40002 强制阻断）
     */
    @Transactional(rollbackFor = Exception.class)
    public ItemCreateResp create(ItemSaveReq req) {
        requireBankExists(req.getBankId());
        QuesItem item = new QuesItem();
        fillBasics(req, item);
        QuesItemAssembler.applyStorage(req, item);
        // 题目来源：默认人工录入；AI 采纳链路显式传 AI
        item.setGenType("AI".equalsIgnoreCase(req.getGenType() == null ? "" : req.getGenType().trim()) ? "AI" : "MANUAL");
        item.setStatus(sysConfigService.initialItemStatus());
        item.setCreateBy(currentUserIdOrNull());

        List<DupHintVO> dupHints = quesDupCheckService.dupCheck(req.getStem(), item.getQuesType(), null);
        if (!dupHints.isEmpty() && sysConfigService.getBoolean(SysConfigService.KEY_DUP_BLOCK, false)) {
            throw new BizException(ErrorCode.QUESTION_DUPLICATED);
        }
        save(item);

        ItemCreateResp resp = new ItemCreateResp();
        resp.setItemId(item.getId());
        resp.setStatus(ItemStatusEnum.toApi(item.getStatus()));
        resp.setDupHints(dupHints);
        return resp;
    }

    /**
     * 编辑题目（QUES-08）：编辑只更新内容。停用（DISABLED）题保持停用不变；
     * 其余状态下审核开关开启时修改后回到待审
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long itemId, ItemSaveReq req) {
        QuesItem item = getById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在: " + itemId);
        }
        requireBankExists(req.getBankId());
        fillBasics(req, item);
        QuesItemAssembler.applyStorage(req, item);
        boolean disabled = item.getStatus() != null
                && item.getStatus() == ItemStatusEnum.DISABLED.getCode();
        if (!disabled && sysConfigService.isAuditRequired()) {
            item.setStatus(ItemStatusEnum.PENDING_AUDIT.getCode());
        }
        item.setUpdateBy(currentUserIdOrNull());
        updateById(item);
    }

    /**
     * 删除/停用题目（QUES-09）：hard=false 停用（DISABLED）；hard=true 物理删除，
     * 仅未被试卷引用且未被使用时允许，否则 40004
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableOrDelete(Long itemId, boolean hard) {
        QuesItem item = getById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在: " + itemId);
        }
        if (!hard) {
            item.setStatus(ItemStatusEnum.DISABLED.getCode());
            item.setUpdateBy(currentUserIdOrNull());
            updateById(item);
            return;
        }
        long references = baseMapper.countPaperReferences(itemId);
        int useCount = item.getUseCount() == null ? 0 : item.getUseCount();
        if (references > 0 || useCount > 0) {
            throw new BizException(ErrorCode.PAPER_SEALED, "题目已被试卷引用或已被使用，不可物理删除，仅可停用");
        }
        baseMapper.physicalDeleteById(itemId);
    }

    /**
     * 题目审核（QUES-13）：ENABLE→启用 / REJECT→驳回至草稿 / DISABLE→停用
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long itemId, String action, String comment) {
        QuesItem item = getById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在: " + itemId);
        }
        AuditActionEnum act = AuditActionEnum.parse(action);
        int current = item.getStatus() == null ? -1 : item.getStatus();
        switch (act) {
            case ENABLE -> {
                if (current == ItemStatusEnum.ENABLED.getCode()) {
                    return;
                }
                item.setStatus(ItemStatusEnum.ENABLED.getCode());
            }
            case REJECT -> {
                if (current != ItemStatusEnum.PENDING_AUDIT.getCode()) {
                    throw new BizException(ErrorCode.QUESTION_NOT_FOUND, "当前状态不允许驳回（仅待审核题目可驳回）");
                }
                item.setStatus(ItemStatusEnum.DRAFT.getCode());
            }
            case DISABLE -> {
                if (current != ItemStatusEnum.ENABLED.getCode()) {
                    throw new BizException(ErrorCode.QUESTION_NOT_FOUND, "当前状态不允许停用（仅启用题目可停用）");
                }
                item.setStatus(ItemStatusEnum.DISABLED.getCode());
            }
        }
        item.setAuditBy(currentUserIdOrNull());
        item.setAuditTime(LocalDateTime.now());
        item.setUpdateBy(currentUserIdOrNull());
        updateById(item);
    }

    // ------------------------------------------------------------------
    // 供 exam-exam 模块复用的组卷/抽题查询能力（仅 status=2 启用题目）
    // ------------------------------------------------------------------

    /**
     * 取启用题目：status=2 且未删除，否则返回 null
     */
    public QuesItem getEnabledItem(Long id) {
        if (id == null) {
            return null;
        }
        QuesItem item = getById(id);
        return item != null && ItemStatusEnum.ENABLED.getCode() == (item.getStatus() == null ? -1 : item.getStatus())
                ? item : null;
    }

    /**
     * 按题库分类（可选含下级）列出启用题目，可按题型/难度过滤
     */
    public List<QuesItem> listEnabledByBanks(List<Long> bankIds, boolean includeChildren,
                                             Integer quesType, Integer difficulty) {
        Set<Long> ids = quesBankService.expandBankIds(bankIds, includeChildren);
        if (ids.isEmpty()) {
            return List.of();
        }
        return list(enabledWrapper(ids, quesType, difficulty));
    }

    /**
     * 随机抽取启用题目；可用不足时返回全部可用，由调用方判断是否抛 40006
     */
    public List<QuesItem> drawRandom(List<Long> bankIds, boolean includeChildren,
                                     Integer quesType, Integer difficulty, int count) {
        if (count <= 0) {
            return List.of();
        }
        Set<Long> ids = quesBankService.expandBankIds(bankIds, includeChildren);
        if (ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<QuesItem> qw = enabledWrapper(ids, quesType, difficulty);
        qw.last("ORDER BY RAND() LIMIT " + count);
        return list(qw);
    }

    /**
     * 统计可用（启用）题量
     */
    public long countEnabled(List<Long> bankIds, boolean includeChildren,
                             Integer quesType, Integer difficulty) {
        Set<Long> ids = quesBankService.expandBankIds(bankIds, includeChildren);
        if (ids.isEmpty()) {
            return 0L;
        }
        return count(enabledWrapper(ids, quesType, difficulty));
    }

    /**
     * 练习抽题（PRAC-01）：bankIds 空表示全库（含子分类展开），difficulties 支持多值
     * （ADVANCED → 2,3）；ORDER BY RAND() 抽 count 道，可用不足时返回全部可用由调用方判 70002
     */
    public List<QuesItem> drawPractice(List<Long> bankIds, Integer quesType, List<Integer> difficulties, int count) {
        QueryWrapper<QuesItem> qw = new QueryWrapper<>();
        if (bankIds != null && !bankIds.isEmpty()) {
            Set<Long> ids = quesBankService.expandBankIds(bankIds, true);
            if (ids.isEmpty()) {
                return List.of();
            }
            qw.in("bank_id", ids);
        }
        qw.eq("status", ItemStatusEnum.ENABLED.getCode());
        qw.eq(quesType != null, "ques_type", quesType);
        if (difficulties != null && !difficulties.isEmpty()) {
            qw.in("difficulty", difficulties);
        }
        qw.last("ORDER BY RAND() LIMIT " + Math.max(count, 1));
        return list(qw);
    }

    // ------------------------------------------------------------------

    private QueryWrapper<QuesItem> enabledWrapper(Set<Long> bankIds, Integer quesType, Integer difficulty) {
        QueryWrapper<QuesItem> qw = new QueryWrapper<>();
        qw.in("bank_id", bankIds)
                .eq("status", ItemStatusEnum.ENABLED.getCode())
                .eq(quesType != null, "ques_type", quesType)
                .eq(difficulty != null, "difficulty", difficulty);
        return qw;
    }

    private void fillBasics(ItemSaveReq req, QuesItem item) {
        item.setBankId(req.getBankId());
        item.setStem(req.getStem());
        item.setAnalysis(req.getAnalysis());
        item.setScoreSuggest(req.getScoreSuggest());
        item.setKnowledgeTags(req.getTags());
        item.setSource(req.getSource());
        item.setSourceLaw(req.getSourceLaw());
        item.setSourceChapter(req.getSourceChapter());
    }

    private void requireBankExists(Long bankId) {
        if (bankId == null || quesBankService.getById(bankId) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "题库分类不存在: " + bankId);
        }
    }

    private ItemPageVO toPageVO(QuesItem item, Map<Long, String> bankNames) {
        ItemPageVO vo = new ItemPageVO();
        vo.setItemId(item.getId());
        vo.setBankId(item.getBankId());
        vo.setBankName(bankNames.get(item.getBankId()));
        vo.setQuesType(QuesTypeEnum.toApi(item.getQuesType()));
        vo.setStemText(StemTextUtils.summary(item.getStem(), 100));
        vo.setDifficulty(DifficultyEnum.toApi(item.getDifficulty()));
        vo.setScoreSuggest(item.getScoreSuggest());
        vo.setTags(item.getKnowledgeTags());
        vo.setStatus(ItemStatusEnum.toApi(item.getStatus()));
        vo.setGenType(item.getGenType() == null || item.getGenType().isBlank() ? "MANUAL" : item.getGenType());
        vo.setUseCount(item.getUseCount());
        vo.setCorrectRate(item.getRightRate());
        vo.setCreatedTime(item.getCreateTime());
        return vo;
    }

    private Map<Long, String> bankNamesOf(List<QuesItem> items) {
        Set<Long> bankIds = items.stream().map(QuesItem::getBankId).collect(Collectors.toSet());
        if (bankIds.isEmpty()) {
            return Map.of();
        }
        return quesBankService.list(new QueryWrapper<QuesBank>()
                        .select("id", "bank_name")
                        .in("id", bankIds))
                .stream()
                .collect(Collectors.toMap(QuesBank::getId, QuesBank::getBankName));
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
