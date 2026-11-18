package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.ExamSaveReq;
import com.group.exam.exam.dto.RetakeReq;
import com.group.exam.exam.entity.AnswerSheet;
import com.group.exam.exam.entity.ExamCandidate;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.entity.Paper;
import com.group.exam.exam.enums.PaperStatusEnum;
import com.group.exam.exam.enums.SessionStatusEnum;
import com.group.exam.exam.mapper.AnswerSheetMapper;
import com.group.exam.exam.mapper.ExamCandidateMapper;
import com.group.exam.exam.mapper.ExamSessionMapper;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.OrgUserService;
import com.group.exam.system.service.SysConfigService;
import com.group.exam.system.service.SysLogService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试场次管理（EXAM-01~06、EXAM-12~14）
 *
 * <p>数据权限：按 create_org_path 前缀过滤；单条操作 checkOrgScope。
 */
@Service
public class ExamSessionService extends ServiceImpl<ExamSessionMapper, ExamSession> {

    private final ExamCandidateMapper examCandidateMapper;
    private final AnswerSheetMapper answerSheetMapper;
    private final PaperService paperService;
    private final GradeRuleService gradeRuleService;
    private final OrgUnitService orgUnitService;
    private final OrgUserService orgUserService;
    private final SysLogService sysLogService;
    private final SheetService sheetService;
    private final SysConfigService sysConfigService;

    public ExamSessionService(ExamCandidateMapper examCandidateMapper, AnswerSheetMapper answerSheetMapper,
                              PaperService paperService, GradeRuleService gradeRuleService,
                              OrgUnitService orgUnitService, OrgUserService orgUserService,
                              SysLogService sysLogService, @Lazy SheetService sheetService,
                              SysConfigService sysConfigService) {
        this.examCandidateMapper = examCandidateMapper;
        this.answerSheetMapper = answerSheetMapper;
        this.paperService = paperService;
        this.gradeRuleService = gradeRuleService;
        this.orgUnitService = orgUnitService;
        this.orgUserService = orgUserService;
        this.sysLogService = sysLogService;
        this.sheetService = sheetService;
        this.sysConfigService = sysConfigService;
    }

    // ---------------------------------------------------------------- EXAM-01 分页查询

    public PageResult<Map<String, Object>> page(String keyword, String status, LocalDateTime startDate,
                                                LocalDateTime endDate, long pageNum, long pageSize) {
        QueryWrapper<ExamSession> qw = new QueryWrapper<>();
        String prefix = SecurityUtils.dataScopePathPrefix();
        qw.likeRight(prefix != null, "create_org_path", prefix);
        if (StringUtils.hasText(keyword)) {
            qw.like("session_name", keyword.trim());
        }
        SessionStatusEnum st = SessionStatusEnum.parseOrNull(status);
        qw.eq(st != null, "session_status", st == null ? null : st.getCode());
        qw.ge(startDate != null, "start_time", startDate);
        qw.le(endDate != null, "start_time", endDate);
        qw.orderByDesc("id");
        Page<ExamSession> page = page(new Page<>(pageNum, pageSize), qw);

        List<Long> sessionIds = page.getRecords().stream().map(ExamSession::getId).toList();
        Map<Long, Long> candidateCounts = countCandidates(sessionIds);
        Map<Long, Long> attendedCounts = countSheets(sessionIds);
        Map<Long, String> orgNames = orgUnitService.mapUnitNames(
                page.getRecords().stream().map(ExamSession::getCreateOrgId).distinct().toList());
        return PageResult.of(page).map(s -> toListItem(s, candidateCounts, attendedCounts, orgNames));
    }

    private Map<String, Object> toListItem(ExamSession s, Map<Long, Long> candidateCounts,
                                           Map<Long, Long> attendedCounts, Map<Long, String> orgNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examId", s.getId());
        m.put("examName", s.getSessionName());
        m.put("paperId", s.getPaperId());
        m.put("paperName", s.getPaperName());
        Object paperType = s.getPaperStrategySnapshot() == null ? null : s.getPaperStrategySnapshot().get("paperType");
        m.put("paperType", paperType != null && ((Number) paperType).intValue() == 2 ? "RANDOM" : "FIXED");
        m.put("startTime", s.getStartTime());
        m.put("endTime", s.getEndTime());
        m.put("durationMin", s.getDurationMin());
        m.put("status", SessionStatusEnum.toApi(s.getSessionStatus()));
        m.put("sessionKind", s.getSessionKind());
        m.put("candidateCount", candidateCounts.getOrDefault(s.getId(), 0L));
        m.put("attendedCount", attendedCounts.getOrDefault(s.getId(), 0L));
        m.put("createdOrgName", orgNames.get(s.getCreateOrgId()));
        m.put("createdTime", s.getCreateTime());
        return m;
    }

    // ---------------------------------------------------------------- EXAM-02 创建

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(ExamSaveReq req) {
        validateSaveReq(req, true);
        Paper paper = paperService.requirePaper(req.getPaperId());
        if (paper.getPaperStatus() == null || paper.getPaperStatus() == PaperStatusEnum.EDITING.getCode()) {
            throw new BizException(ErrorCode.PAPER_SEALED, "试卷为草稿状态，须先完成组卷启用");
        }
        LoginUser user = SecurityUtils.getLoginUser();

        ExamSession session = new ExamSession();
        session.setSessionName(req.getExamName().trim());
        requireSessionNameUnique(session.getSessionName(), null);
        session.setPaperId(paper.getId());
        session.setPaperName(paper.getPaperName());
        session.setPaperVersion(paper.getVersion());
        session.setPaperStrategySnapshot(buildStrategySnapshot(paper, req.getNotice()));
        fillRuleFields(session, req);
        session.setSessionStatus(SessionStatusEnum.NOT_STARTED.getCode());
        session.setSessionKind(1);
        session.setCreateOrgId(user.getOrgId());
        session.setCreateOrgPath(user.getOrgPath());
        session.setCreateBy(user.getUserId());
        save(session);
        // 关联后试卷自动封存
        paperService.sealOnReferenced(paper.getId());
        sysLogService.log(1, "EXAM", "创建测试场次",
                "创建场次[" + session.getSessionName() + "]，试卷[" + paper.getPaperName() + "]", 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", session.getId());
        result.put("status", SessionStatusEnum.toApi(session.getSessionStatus()));
        return result;
    }

    /** 试卷策略快照：抽题规则/及格线/判分规则/等级规则id与条目/须知 */
    private Map<String, Object> buildStrategySnapshot(Paper paper, String notice) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paperId", paper.getId());
        snapshot.put("paperName", paper.getPaperName());
        snapshot.put("paperVersion", paper.getVersion());
        snapshot.put("paperType", paper.getPaperType());
        snapshot.put("totalScore", paper.getTotalScore());
        snapshot.put("passScore", paper.getPassScore());
        snapshot.put("multiJudgeRule", paper.getMultiJudgeRule());
        snapshot.put("samePaper", paper.getSamePaper());
        snapshot.put("randomStrategy", paper.getRandomStrategy());
        snapshot.put("gradeRuleId", paper.getGradeRuleId());
        snapshot.put("gradeRuleItems", gradeRuleService.snapshotItems(paper.getGradeRuleId()));
        snapshot.put("notice", notice);
        return snapshot;
    }

    private void fillRuleFields(ExamSession session, ExamSaveReq req) {
        session.setStartTime(req.getStartTime());
        session.setEndTime(req.getEndTime());
        // 时长未传时取系统参数 exam.defaultDurationMin（V1.2 默认 10 分钟，管理员可配）
        session.setDurationMin(req.getDurationMin() != null ? req.getDurationMin()
                : sysConfigService.getInt("exam.defaultDurationMin", 10));
        session.setLateLimitMin(req.getLateEnterMin() == null ? 0 : req.getLateEnterMin());
        session.setSwitchLimit(req.getSwitchScreenLimit() == null ? 3 : req.getSwitchScreenLimit());
        session.setAllowEarlySubmit(req.getAllowEarlySubmit() == null || req.getAllowEarlySubmit() ? 1 : 0);
        session.setEarlySubmitAfterMin(req.getEarlySubmitAfterMin() == null ? 0 : req.getEarlySubmitAfterMin());
        session.setShuffleQuestion(req.getShuffleQuestion() == null || req.getShuffleQuestion() ? 1 : 0);
        session.setShuffleOption(req.getShuffleOption() == null || req.getShuffleOption() ? 1 : 0);
    }

    // ---------------------------------------------------------------- EXAM-03 详情

    public Map<String, Object> detail(Long examId) {
        ExamSession session = requireInScope(examId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examId", session.getId());
        m.put("examName", session.getSessionName());
        m.put("paperId", session.getPaperId());
        m.put("paperName", session.getPaperName());
        m.put("paperVersion", session.getPaperVersion());
        Map<String, Object> strategy = session.getPaperStrategySnapshot() == null
                ? Map.of() : session.getPaperStrategySnapshot();
        Object paperType = strategy.get("paperType");
        m.put("paperType", paperType != null && ((Number) paperType).intValue() == 2 ? "RANDOM" : "FIXED");
        m.put("totalScore", strategy.get("totalScore"));
        m.put("passScore", strategy.get("passScore"));
        m.put("multiScoreRule", com.group.exam.exam.enums.MultiScoreRuleEnum.toApi(
                strategy.get("multiJudgeRule") instanceof Number n ? n.intValue() : null));
        m.put("randomStrategy", strategy.get("randomStrategy"));
        m.put("gradeRuleId", strategy.get("gradeRuleId"));
        m.put("gradeRuleItems", strategy.get("gradeRuleItems"));
        m.put("notice", strategy.get("notice"));
        m.put("startTime", session.getStartTime());
        m.put("endTime", session.getEndTime());
        m.put("durationMin", session.getDurationMin());
        m.put("lateEnterMin", session.getLateLimitMin());
        m.put("switchScreenLimit", session.getSwitchLimit());
        m.put("allowEarlySubmit", session.getAllowEarlySubmit() != null && session.getAllowEarlySubmit() == 1);
        m.put("earlySubmitAfterMin", session.getEarlySubmitAfterMin());
        m.put("shuffleQuestion", session.getShuffleQuestion() != null && session.getShuffleQuestion() == 1);
        m.put("shuffleOption", session.getShuffleOption() != null && session.getShuffleOption() == 1);
        m.put("status", SessionStatusEnum.toApi(session.getSessionStatus()));
        m.put("sessionKind", session.getSessionKind());
        m.put("relatedSessionId", session.getRelatedSessionId());
        m.put("scorePublishTime", session.getScorePublishTime());
        m.put("createOrgId", session.getCreateOrgId());
        m.put("createdOrgName", orgUnitService.mapUnitNames(List.of(session.getCreateOrgId())).get(session.getCreateOrgId()));
        m.put("createdTime", session.getCreateTime());

        long candidateCount = examCandidateMapper.selectCount(
                new QueryWrapper<ExamCandidate>().eq("session_id", examId));
        long attendedCount = answerSheetMapper.selectCount(
                new QueryWrapper<AnswerSheet>().eq("session_id", examId));
        long submittedCount = answerSheetMapper.selectCount(
                new QueryWrapper<AnswerSheet>().eq("session_id", examId).eq("sheet_status", 2));
        m.put("candidateCount", candidateCount);
        m.put("attendedCount", attendedCount);
        m.put("submittedCount", submittedCount);
        List<Map<String, Object>> scoreRows = answerSheetMapper.selectMaps(new QueryWrapper<AnswerSheet>()
                .select("AVG(total_score) AS avg_score")
                .eq("session_id", examId).eq("sheet_status", 2).isNotNull("total_score"));
        Map<String, Object> scoreRow = scoreRows.isEmpty() ? null : scoreRows.get(0);
        m.put("avgScore", scoreRow == null ? null : scoreRow.get("avg_score"));
        m.put("notifySentTime", null);

        // 补考配置（本场次为正考且已设置补考时返回）
        ExamSession retake = getOne(new QueryWrapper<ExamSession>()
                .eq("related_session_id", examId).eq("session_kind", 2), false);
        if (retake != null) {
            Map<String, Object> retakeConfig = new LinkedHashMap<>();
            retakeConfig.put("examId", retake.getId());
            retakeConfig.put("startTime", retake.getStartTime());
            retakeConfig.put("endTime", retake.getEndTime());
            retakeConfig.put("status", SessionStatusEnum.toApi(retake.getSessionStatus()));
            m.put("retakeConfig", retakeConfig);
        } else {
            m.put("retakeConfig", null);
        }
        return m;
    }

    // ---------------------------------------------------------------- EXAM-04 编辑（仅未开始）

    @Transactional(rollbackFor = Exception.class)
    public void update(Long examId, ExamSaveReq req) {
        ExamSession session = requireInScope(examId);
        if (session.getSessionStatus() != SessionStatusEnum.NOT_STARTED.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "仅未开始场次可编辑");
        }
        long candidateCount = examCandidateMapper.selectCount(
                new QueryWrapper<ExamCandidate>().eq("session_id", examId));
        if (candidateCount > 0) {
            // 已下发名单后仅可改测试须知
            if (req.getNotice() != null) {
                Map<String, Object> snapshot = session.getPaperStrategySnapshot() == null
                        ? new LinkedHashMap<>() : new LinkedHashMap<>(session.getPaperStrategySnapshot());
                snapshot.put("notice", req.getNotice());
                session.setPaperStrategySnapshot(snapshot);
                session.setUpdateBy(SecurityUtils.getUserId());
                updateById(session);
            }
            return;
        }
        validateSaveReq(req, true);
        Paper paper = paperService.requirePaper(req.getPaperId());
        if (paper.getPaperStatus() == null || paper.getPaperStatus() == PaperStatusEnum.EDITING.getCode()) {
            throw new BizException(ErrorCode.PAPER_SEALED, "试卷为草稿状态，须先完成组卷启用");
        }
        String oldName = session.getSessionName();
        session.setSessionName(req.getExamName().trim());
        if (!session.getSessionName().equals(oldName)) {
            requireSessionNameUnique(session.getSessionName(), examId);
        }
        if (!paper.getId().equals(session.getPaperId())) {
            session.setPaperId(paper.getId());
            session.setPaperName(paper.getPaperName());
            session.setPaperVersion(paper.getVersion());
            paperService.sealOnReferenced(paper.getId());
        }
        session.setPaperStrategySnapshot(buildStrategySnapshot(paper, req.getNotice()));
        fillRuleFields(session, req);
        session.setUpdateBy(SecurityUtils.getUserId());
        updateById(session);
    }

    // ---------------------------------------------------------------- EXAM-05 删除（未开始且未生成名单）

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long examId) {
        ExamSession session = requireInScope(examId);
        if (session.getSessionStatus() != SessionStatusEnum.NOT_STARTED.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "仅未开始场次可删除");
        }
        long candidateCount = examCandidateMapper.selectCount(
                new QueryWrapper<ExamCandidate>().eq("session_id", examId));
        if (candidateCount > 0) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "已生成考生名单的场次只能取消，不可删除");
        }
        removeById(examId);
        sysLogService.log(1, "EXAM", "删除测试场次", "删除场次[" + session.getSessionName() + "]", 1);
    }

    // ---------------------------------------------------------------- EXAM-06 状态流转

    @Transactional(rollbackFor = Exception.class)
    public void transit(Long examId, String action) {
        ExamSession session = requireInScope(examId);
        if (!StringUtils.hasText(action)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "action 不能为空（START/END/CANCEL）");
        }
        int current = session.getSessionStatus();
        switch (action.trim().toUpperCase()) {
            case "START" -> {
                if (current != SessionStatusEnum.NOT_STARTED.getCode()) {
                    throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "仅未开始场次可开考");
                }
                session.setSessionStatus(SessionStatusEnum.IN_PROGRESS.getCode());
            }
            case "END" -> {
                if (current != SessionStatusEnum.IN_PROGRESS.getCode()) {
                    throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "仅进行中场次可结束");
                }
                session.setSessionStatus(SessionStatusEnum.ENDED.getCode());
                // 结束后未交卷答卷强制收卷
                sheetService.forceSubmitSheets(examId, null, "场次结束强制收卷");
            }
            case "CANCEL" -> {
                if (current != SessionStatusEnum.NOT_STARTED.getCode()) {
                    throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "仅未开始场次可取消");
                }
                session.setSessionStatus(SessionStatusEnum.CANCELED.getCode());
            }
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法状态流转动作: " + action);
        }
        session.setUpdateBy(SecurityUtils.getUserId());
        updateById(session);
        sysLogService.log(1, "EXAM", "场次状态流转",
                "场次[" + session.getSessionName() + "] 执行 " + action.trim().toUpperCase()
                        + " → " + SessionStatusEnum.toApi(session.getSessionStatus()), 1);
    }

    // ---------------------------------------------------------------- EXAM-12 延长测试时间

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> extend(Long examId, Integer extendMin) {
        ExamSession session = requireInScope(examId);
        if (extendMin == null || extendMin <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "extendMin 必须为正整数");
        }
        int status = session.getSessionStatus();
        if (status != SessionStatusEnum.IN_PROGRESS.getCode() && status != SessionStatusEnum.NOT_STARTED.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "仅未开始/进行中场次可延长时间");
        }
        session.setEndTime(session.getEndTime().plusMinutes(extendMin));
        session.setUpdateBy(SecurityUtils.getUserId());
        updateById(session);
        // 在考考生剩余时间同步延长：deadline = min(进入时刻+时长, 新窗口结束)
        List<AnswerSheet> sheets = answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                .eq("session_id", examId).eq("sheet_status", 1));
        for (AnswerSheet sheet : sheets) {
            LocalDateTime byDuration = sheet.getStartTime().plusMinutes(session.getDurationMin());
            LocalDateTime newDeadline = byDuration.isBefore(session.getEndTime()) ? byDuration : session.getEndTime();
            if (sheet.getServerDeadline() == null || newDeadline.isAfter(sheet.getServerDeadline())) {
                AnswerSheet update = new AnswerSheet();
                update.setId(sheet.getId());
                update.setServerDeadline(newDeadline);
                answerSheetMapper.updateById(update);
            }
        }
        sysLogService.log(1, "EXAM", "延长测试时间",
                "场次[" + session.getSessionName() + "] 延长 " + extendMin + " 分钟", 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", examId);
        result.put("endTime", session.getEndTime());
        return result;
    }

    // ---------------------------------------------------------------- EXAM-13 强制收卷

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> forceSubmit(Long examId, List<Long> userIds, String reason) {
        ExamSession session = requireInScope(examId);
        int status = session.getSessionStatus();
        if (status != SessionStatusEnum.IN_PROGRESS.getCode() && status != SessionStatusEnum.ENDED.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "仅进行中/已结束场次可强制收卷");
        }
        int forced = sheetService.forceSubmitSheets(examId, userIds,
                StringUtils.hasText(reason) ? reason : "管理员强制收卷");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("forcedCount", forced);
        return result;
    }

    // ---------------------------------------------------------------- EXAM-14 补考设置

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setupRetake(Long examId, RetakeReq req) {
        ExamSession source = requireInScope(examId);
        int status = source.getSessionStatus();
        if (status != SessionStatusEnum.ENDED.getCode() && status != SessionStatusEnum.SCORED_PUBLISHED.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "仅已结束场次可设置补考");
        }
        if (req.getRetakeStartTime() == null || req.getRetakeEndTime() == null
                || !req.getRetakeEndTime().isAfter(req.getRetakeStartTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "补考时间窗口非法");
        }
        ExamSession existing = getOne(new QueryWrapper<ExamSession>()
                .eq("related_session_id", examId).eq("session_kind", 2), false);
        if (existing != null) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "该场次已设置补考: examId=" + existing.getId());
        }
        LoginUser user = SecurityUtils.getLoginUser();

        ExamSession retake = new ExamSession();
        retake.setSessionName(source.getSessionName() + "（补考）");
        requireSessionNameUnique(retake.getSessionName(), null);
        retake.setPaperId(source.getPaperId());
        retake.setPaperName(source.getPaperName());
        retake.setPaperVersion(source.getPaperVersion());
        retake.setPaperStrategySnapshot(source.getPaperStrategySnapshot());
        retake.setStartTime(req.getRetakeStartTime());
        retake.setEndTime(req.getRetakeEndTime());
        retake.setDurationMin(source.getDurationMin());
        retake.setLateLimitMin(source.getLateLimitMin());
        retake.setSwitchLimit(source.getSwitchLimit());
        retake.setAllowEarlySubmit(source.getAllowEarlySubmit());
        retake.setEarlySubmitAfterMin(source.getEarlySubmitAfterMin());
        retake.setShuffleQuestion(source.getShuffleQuestion());
        retake.setShuffleOption(source.getShuffleOption());
        retake.setSessionStatus(SessionStatusEnum.NOT_STARTED.getCode());
        retake.setSessionKind(2);
        retake.setRelatedSessionId(source.getId());
        retake.setCreateOrgId(user.getOrgId());
        retake.setCreateOrgPath(user.getOrgPath());
        retake.setCreateBy(user.getUserId());
        save(retake);

        // 补考名单：指定人员；未指定则取正考缺考/不及格人员
        List<Long> userIds = req.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            userIds = retakeDefaultUsers(source);
        }
        int added = 0;
        for (Long userId : userIds) {
            OrgUser u = orgUserService.getById(userId);
            if (u == null || examCandidateMapper.selectIncludeDeleted(retake.getId(), userId) != null) {
                continue;
            }
            ExamCandidate c = new ExamCandidate();
            c.setSessionId(retake.getId());
            c.setUserId(u.getId());
            c.setUserName(u.getUserName());
            c.setJobNo(u.getJobNo());
            c.setOrgId(u.getOrgId());
            c.setOrgPath(u.getOrgPath());
            c.setOrgName(orgUnitService.mapUnitNames(List.of(u.getOrgId())).get(u.getOrgId()));
            c.setCandidateStatus(0);
            c.setSource(2);
            c.setNotifyStatus(0);
            c.setCreateBy(user.getUserId());
            examCandidateMapper.insert(c);
            added++;
        }
        sysLogService.log(1, "EXAM", "补考设置",
                "场次[" + source.getSessionName() + "] 设置补考 examId=" + retake.getId() + "，补考人数 " + added, 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", retake.getId());
        result.put("candidateCount", added);
        return result;
    }

    /** 正考缺考(candidate_status=2) 或不及格(total_score < passScore) 人员 */
    private List<Long> retakeDefaultUsers(ExamSession source) {
        List<Long> userIds = new ArrayList<>();
        examCandidateMapper.selectList(new QueryWrapper<ExamCandidate>()
                        .select("user_id").eq("session_id", source.getId()).eq("candidate_status", 2))
                .forEach(c -> userIds.add(c.getUserId()));
        Object passScore = source.getPaperStrategySnapshot() == null
                ? null : source.getPaperStrategySnapshot().get("passScore");
        if (passScore != null) {
            answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                            .select("user_id").eq("session_id", source.getId())
                            .eq("sheet_status", 2).lt("total_score", passScore))
                    .forEach(s -> {
                        if (!userIds.contains(s.getUserId())) {
                            userIds.add(s.getUserId());
                        }
                    });
        }
        return userIds;
    }

    // ---------------------------------------------------------------- 内部工具

    public ExamSession requireInScope(Long examId) {
        ExamSession session = getById(examId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "测试场次不存在: " + examId);
        }
        SecurityUtils.checkOrgScope(session.getCreateOrgPath());
        return session;
    }

    private void validateSaveReq(ExamSaveReq req, boolean requireAll) {
        if (!StringUtils.hasText(req.getExamName())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "测试名称不能为空");
        }
        if (req.getPaperId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "必须关联试卷 paperId");
        }
        if (req.getStartTime() == null || req.getEndTime() == null || !req.getEndTime().isAfter(req.getStartTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "测试时间窗口非法（endTime 须晚于 startTime）");
        }
        if (req.getDurationMin() != null && req.getDurationMin() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "测试时长必须为正整数");
        }
    }

    /** 测试名称唯一性校验：未删除场次间不重名；excludeId 用于编辑时排除自身 */
    private void requireSessionNameUnique(String sessionName, Long excludeId) {
        boolean dup = lambdaQuery()
                .eq(ExamSession::getSessionName, sessionName)
                .ne(excludeId != null, ExamSession::getId, excludeId)
                .select(ExamSession::getId)
                .list().stream().findFirst().isPresent();
        if (dup) {
            throw new BizException(ErrorCode.PARAM_ERROR, "测试名称已存在，请更换名称");
        }
    }

    private Map<Long, Long> countCandidates(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = examCandidateMapper.selectMaps(new QueryWrapper<ExamCandidate>()
                .select("session_id", "COUNT(*) AS cnt")
                .in("session_id", sessionIds).groupBy("session_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("session_id")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    private Map<Long, Long> countSheets(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = answerSheetMapper.selectMaps(new QueryWrapper<AnswerSheet>()
                .select("session_id", "COUNT(*) AS cnt")
                .in("session_id", sessionIds).groupBy("session_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("session_id")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }
}
