package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.EnterExamReq;
import com.group.exam.exam.dto.SaveAnswersReq;
import com.group.exam.exam.dto.ScreenEventReq;
import com.group.exam.exam.entity.AnswerDetail;
import com.group.exam.exam.entity.AnswerSheet;
import com.group.exam.exam.entity.ExamCandidate;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.enums.SessionStatusEnum;
import com.group.exam.exam.mapper.AnswerDetailMapper;
import com.group.exam.exam.mapper.AnswerSheetMapper;
import com.group.exam.exam.mapper.ExamCandidateMapper;
import com.group.exam.exam.mapper.ExamSessionMapper;
import com.group.exam.exam.support.RandomStrategy;
import com.group.exam.question.enums.QuesTypeEnum;
import com.group.exam.system.service.SysConfigService;
import com.group.exam.system.service.SysLogService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考生端在线考试（TAKE-01~09）与交卷/强制收卷
 */
@Service
public class SheetService extends ServiceImpl<AnswerSheetMapper, AnswerSheet> {

    /** 答题会话令牌 key 前缀 */
    public static final String SHEET_TOKEN_KEY = "exam:sheet:token:";
    /** 答案保存批次去重 key 前缀 */
    public static final String ANSWER_BATCH_KEY = "exam:batch:";
    /** 超时宽松窗口（秒）：网络延迟容忍 */
    private static final long GRACE_SECONDS = 30;

    private final ExamSessionMapper examSessionMapper;
    private final ExamCandidateMapper examCandidateMapper;
    private final AnswerDetailMapper answerDetailMapper;
    private final PaperSnapshotService paperSnapshotService;
    private final GradingService gradingService;
    private final SysConfigService sysConfigService;
    private final SysLogService sysLogService;
    private final StringRedisTemplate redisTemplate;

    public SheetService(ExamSessionMapper examSessionMapper, ExamCandidateMapper examCandidateMapper,
                        AnswerDetailMapper answerDetailMapper, PaperSnapshotService paperSnapshotService,
                        GradingService gradingService, SysConfigService sysConfigService,
                        SysLogService sysLogService, StringRedisTemplate redisTemplate) {
        this.examSessionMapper = examSessionMapper;
        this.examCandidateMapper = examCandidateMapper;
        this.answerDetailMapper = answerDetailMapper;
        this.paperSnapshotService = paperSnapshotService;
        this.gradingService = gradingService;
        this.sysConfigService = sysConfigService;
        this.sysLogService = sysLogService;
        this.redisTemplate = redisTemplate;
    }

    // ---------------------------------------------------------------- TAKE-01 待考/历史列表

    public PageResult<Map<String, Object>> pageMyExams(Long userId, String type, long pageNum, long pageSize) {
        boolean history = "HISTORY".equalsIgnoreCase(type);
        QueryWrapper<ExamCandidate> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        qw.inSql("session_id", "SELECT id FROM t_exam_session WHERE deleted = 0 AND session_status "
                + (history ? "IN (2,3,4)" : "IN (0,1)"));
        qw.orderByDesc("id");
        Page<ExamCandidate> page = examCandidateMapper.selectPage(new Page<>(pageNum, pageSize), qw);

        List<Long> sessionIds = page.getRecords().stream().map(ExamCandidate::getSessionId).toList();
        Map<Long, ExamSession> sessionMap = sessionIds.isEmpty() ? Map.of()
                : examSessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(ExamSession::getId, Function.identity()));
        Map<Long, AnswerSheet> sheetMap = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            for (AnswerSheet s : list(new QueryWrapper<AnswerSheet>()
                    .eq("user_id", userId).in("session_id", sessionIds))) {
                sheetMap.put(s.getSessionId(), s);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        return PageResult.of(page).map(c -> {
            ExamSession s = sessionMap.get(c.getSessionId());
            AnswerSheet sheet = sheetMap.get(c.getSessionId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("examId", c.getSessionId());
            m.put("examName", s == null ? null : s.getSessionName());
            m.put("startTime", s == null ? null : s.getStartTime());
            m.put("endTime", s == null ? null : s.getEndTime());
            m.put("durationMin", s == null ? null : s.getDurationMin());
            m.put("status", s == null ? null : SessionStatusEnum.toApi(s.getSessionStatus()));
            m.put("sessionKind", s == null ? null : s.getSessionKind());
            m.put("mySheetStatus", CandidateService.sheetStatusOf(c, sheet));
            m.put("sheetId", sheet == null ? null : sheet.getId());
            String cannotReason = s == null ? "场次不存在" : cannotEnterReason(s, sheet, now);
            m.put("canEnter", cannotReason == null);
            m.put("cannotEnterReason", cannotReason);
            m.put("scorePublished", s != null && s.getSessionStatus() == SessionStatusEnum.SCORED_PUBLISHED.getCode());
            return m;
        });
    }

    /** 迟到进入截止时间：lateLimitMin<=0（不允许迟到）按分钟粒度放行——开考时刻所在分钟内均可进入，避免秒级精度导致刚到点即被拒 */
    private LocalDateTime lateDeadline(ExamSession s) {
        int lateMin = s.getLateLimitMin() == null ? 0 : s.getLateLimitMin();
        LocalDateTime deadline = s.getStartTime().plusMinutes(lateMin);
        return lateMin <= 0 ? deadline.plusSeconds(59) : deadline;
    }

    private String cannotEnterReason(ExamSession s, AnswerSheet sheet, LocalDateTime now) {
        if (sheet != null && sheet.getSheetStatus() != null && sheet.getSheetStatus() == 2) {
            return "已交卷";
        }
        if (s.getSessionStatus() == SessionStatusEnum.CANCELED.getCode()) {
            return "场次已取消";
        }
        if (s.getSessionStatus() != SessionStatusEnum.NOT_STARTED.getCode()
                && s.getSessionStatus() != SessionStatusEnum.IN_PROGRESS.getCode()) {
            return "场次已结束";
        }
        if (now.isBefore(s.getStartTime())) {
            return "未到开考时间";
        }
        if (now.isAfter(s.getEndTime())) {
            return "已过考试截止时间";
        }
        if (sheet == null && now.isAfter(lateDeadline(s))) {
            return "已超过允许迟到进入时限";
        }
        return null;
    }

    // ---------------------------------------------------------------- TAKE-02 进入考试

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> enter(Long examId, EnterExamReq req) {
        Long userId = SecurityUtils.getUserId();
        ExamSession session = examSessionMapper.selectById(examId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考试场次不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.getSessionStatus() == SessionStatusEnum.CANCELED.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "场次已取消");
        }
        if (now.isBefore(session.getStartTime()) || now.isAfter(session.getEndTime())) {
            throw new BizException(ErrorCode.NOT_IN_EXAM_WINDOW);
        }
        ExamCandidate candidate = examCandidateMapper.selectOne(new QueryWrapper<ExamCandidate>()
                .eq("session_id", examId).eq("user_id", userId), false);
        if (candidate == null) {
            throw new BizException(ErrorCode.NOT_IN_CANDIDATE_LIST);
        }
        AnswerSheet existing = getOne(new QueryWrapper<AnswerSheet>()
                .eq("session_id", examId).eq("user_id", userId), false);
        if (existing != null) {
            if (existing.getSheetStatus() != null && existing.getSheetStatus() == 2) {
                throw new BizException(ErrorCode.ALREADY_SUBMITTED);
            }
            // 重复进入/断线重进：幂等返回原答卷（KICK_OLD 策略下新令牌生效，旧终端请求随后失效）
            String strategy = sysConfigService.getValue("exam.multiTerminal");
            if ("REJECT_NEW".equalsIgnoreCase(strategy) && req.getTerminal() != null
                    && existing.getTerminal() != null && !existing.getTerminal().equals(req.getTerminal())) {
                throw new BizException(ErrorCode.OTHER_TERMINAL_ONLINE);
            }
            return enterResult(session, existing, "RESUME", req);
        }
        if (now.isAfter(lateDeadline(session))) {
            throw new BizException(ErrorCode.LATE_LIMIT_EXCEEDED);
        }
        // 首次进入：生成试卷快照（固定/随机一人一卷/全场同卷）与逐题明细
        Map<String, Object> snapshot = paperSnapshotService.buildForCandidate(session);
        LocalDateTime deadline = deadlineOf(session, now);

        AnswerSheet sheet = new AnswerSheet();
        sheet.setSessionId(session.getId());
        sheet.setCandidateId(candidate.getId());
        sheet.setUserId(userId);
        sheet.setUserName(candidate.getUserName());
        sheet.setJobNo(candidate.getJobNo());
        sheet.setOrgId(candidate.getOrgId());
        sheet.setOrgPath(candidate.getOrgPath());
        sheet.setOrgName(candidate.getOrgName());
        sheet.setPaperId(session.getPaperId());
        sheet.setPaperSnapshot(snapshot);
        sheet.setStartTime(now);
        sheet.setServerDeadline(deadline);
        sheet.setSwitchCount(0);
        sheet.setTerminal(req == null ? null : req.getTerminal());
        sheet.setSessionToken(UUID.randomUUID().toString());
        sheet.setMarkStatus(0);
        sheet.setSheetStatus(1);
        sheet.setCreateBy(userId);
        save(sheet);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) snapshot.get("questions");
        for (Map<String, Object> q : questions) {
            AnswerDetail detail = new AnswerDetail();
            detail.setSheetId(sheet.getId());
            detail.setSessionId(session.getId());
            detail.setUserId(userId);
            detail.setQuesId(RandomStrategy.asLong(q.get("quesId")));
            detail.setQuesType(RandomStrategy.asInt(q.get("quesType"), 0));
            detail.setSortNo(RandomStrategy.asInt(q.get("seq"), 0));
            detail.setQuesScore(RandomStrategy.asBigDecimal(q.get("score")));
            // 选项乱序映射快照（展示key→原始key，V1.1），未乱序题型为 NULL
            if (q.get("optionOrderMap") instanceof Map<?, ?> optionOrder) {
                detail.setOptionOrder((Map<String, Object>) optionOrder);
            }
            detail.setMarkStatus(0);
            detail.setCreateBy(userId);
            answerDetailMapper.insert(detail);
        }
        cacheToken(sheet);
        return enterResult(session, sheet, "NEW", req);
    }

    private Map<String, Object> enterResult(ExamSession session, AnswerSheet sheet, String resumeFrom, EnterExamReq req) {
        if (req != null && StringUtils.hasText(req.getTerminal())) {
            AnswerSheet update = new AnswerSheet();
            update.setId(sheet.getId());
            update.setTerminal(req.getTerminal());
            updateById(update);
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sheetId", sheet.getId());
        m.put("serverTime", now);
        m.put("deadlineTime", sheet.getServerDeadline());
        m.put("remainingSec", remainSec(sheet, now));
        m.put("resumeFrom", resumeFrom);
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("durationMin", session.getDurationMin());
        rules.put("switchScreenLimit", session.getSwitchLimit());
        rules.put("allowEarlySubmit", session.getAllowEarlySubmit() != null && session.getAllowEarlySubmit() == 1);
        rules.put("earlySubmitAfterMin", session.getEarlySubmitAfterMin());
        rules.put("notice", session.getPaperStrategySnapshot() == null
                ? null : session.getPaperStrategySnapshot().get("notice"));
        m.put("examRules", rules);
        return m;
    }

    // ---------------------------------------------------------------- TAKE-03 服务端校时

    public Map<String, Object> serverTime() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("serverTime", now);
        m.put("epochMillis", System.currentTimeMillis());
        return m;
    }

    // ---------------------------------------------------------------- TAKE-04 拉取试卷（脱敏）

    public Map<String, Object> paper(Long examId, Long sheetId) {
        AnswerSheet sheet = requireOwnActiveSheet(sheetId);
        if (!sheet.getSessionId().equals(examId)) {
            throw new BizException(ErrorCode.SHEET_NOT_FOUND, "答卷不属于该场次");
        }
        List<AnswerDetail> details = answerDetailMapper.selectList(new QueryWrapper<AnswerDetail>()
                .eq("sheet_id", sheetId).orderByAsc("sort_no"));
        Map<Long, Long> detailIdByQues = details.stream()
                .collect(Collectors.toMap(AnswerDetail::getQuesId, AnswerDetail::getId));

        Map<String, Object> snapshot = sheet.getPaperSnapshot();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) snapshot.get("questions");
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", q.get("seq"));
            item.put("sheetQuesId", detailIdByQues.get(RandomStrategy.asLong(q.get("quesId"))));
            item.put("quesTypeCode", q.get("quesType"));
            item.put("stem", q.get("stem"));
            item.put("options", sanitizeOptions(q.get("options")));
            item.put("score", q.get("score"));
            sanitized.add(item);
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examId", examId);
        m.put("sheetId", sheetId);
        m.put("paperName", snapshot.get("paperName"));
        m.put("totalScore", snapshot.get("totalScore"));
        ExamSession session = examSessionMapper.selectById(examId);
        m.put("durationMin", session == null ? null : session.getDurationMin());
        m.put("remainingSec", remainSec(sheet, now));
        m.put("serverTime", now);
        m.put("serverDeadline", sheet.getServerDeadline());
        m.put("sections", PaperService.toSections(sanitized));
        // 断线续考：已保存答案一并下发
        List<Map<String, Object>> answers = new ArrayList<>();
        for (AnswerDetail d : details) {
            if (d.getUserAnswer() != null) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("sheetQuesId", d.getId());
                a.put("answer", d.getUserAnswer());
                answers.add(a);
            }
        }
        m.put("answers", answers);
        return m;
    }

    /** 选项脱敏：仅保留展示 key 与内容（绝不含 isAnswer 等标记） */
    private List<Map<String, Object>> sanitizeOptions(Object optionsObj) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (optionsObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> opt) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    Object key = opt.get("key") != null ? opt.get("key") : opt.get("optKey");
                    item.put("optKey", key);
                    item.put("content", opt.get("content"));
                    result.add(item);
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- TAKE-05 批量保存答案

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveAnswers(Long sheetId, SaveAnswersReq req) {
        Long userId = SecurityUtils.getUserId();
        AnswerSheet sheet = requireOwnSheet(sheetId, userId);
        // 批次去重：重复 clientBatchId 直接返回成功不重复写
        if (req != null && StringUtils.hasText(req.getClientBatchId())) {
            Boolean fresh = redisTemplate.opsForValue().setIfAbsent(
                    ANSWER_BATCH_KEY + sheetId + ":" + req.getClientBatchId(), "1", Duration.ofMinutes(10));
            if (Boolean.FALSE.equals(fresh)) {
                return saveResult(sheet, 0);
            }
        }
        if (sheet.getSheetStatus() == null || sheet.getSheetStatus() != 1) {
            throw new BizException(ErrorCode.ALREADY_SUBMITTED);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean overDeadline = sheet.getServerDeadline() != null && now.isAfter(sheet.getServerDeadline());
        if (overDeadline && now.isAfter(sheet.getServerDeadline().plusSeconds(GRACE_SECONDS))) {
            // 超出宽松窗口：到时自动收卷，不再接收保存
            submitInternal(sheet, 2);
            return saveResult(sheet, 0);
        }
        Map<Long, AnswerDetail> detailMap = answerDetailMapper.selectList(new QueryWrapper<AnswerDetail>()
                        .eq("sheet_id", sheetId))
                .stream().collect(Collectors.toMap(AnswerDetail::getId, Function.identity()));
        int saved = 0;
        if (req != null && req.getAnswers() != null) {
            for (SaveAnswersReq.AnswerItem item : req.getAnswers()) {
                if (item.getSheetQuesId() == null) {
                    continue;
                }
                AnswerDetail detail = detailMap.get(item.getSheetQuesId());
                if (detail == null) {
                    continue;
                }
                detail.setUserAnswer(canonicalAnswer(item.getAnswer()));
                detail.setAnswerTime(now);
                answerDetailMapper.updateById(detail);
                saved++;
            }
        }
        if (overDeadline) {
            // 宽松窗口内保存后到时自动收卷
            submitInternal(sheet, 2);
        }
        return saveResult(sheet, saved);
    }

    /** 答案结构归一：{selected}→{keys}；blanks/text 原样 */
    private Map<String, Object> canonicalAnswer(Map<String, Object> answer) {
        if (answer == null) {
            return null;
        }
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

    private Map<String, Object> saveResult(AnswerSheet sheet, int saved) {
        AnswerSheet fresh = getById(sheet.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("savedCount", saved);
        m.put("serverTime", LocalDateTime.now());
        m.put("remainingSec", remainSec(fresh, LocalDateTime.now()));
        m.put("submitStatus", submitStatusOf(fresh));
        return m;
    }

    // ---------------------------------------------------------------- TAKE-06 切屏/离开上报

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> screenEvent(Long sheetId, ScreenEventReq req) {
        AnswerSheet sheet = requireOwnActiveSheet(sheetId);
        ExamSession session = examSessionMapper.selectById(sheet.getSessionId());
        int limit = session == null || session.getSwitchLimit() == null ? 3 : session.getSwitchLimit();
        String action = "WARN";
        if (req != null && "LEAVE".equalsIgnoreCase(req.getEventType())) {
            sheet.setSwitchCount((sheet.getSwitchCount() == null ? 0 : sheet.getSwitchCount()) + 1);
            if (sheet.getSwitchCount() > limit) {
                // 超限：自动强制交卷 + 考试异常日志
                submitInternal(sheet, 4);
                sysLogService.log(3, "EXAM", "SWITCH_EXCEED",
                        "考生[" + sheet.getUserName() + "(" + sheet.getJobNo() + ")] 场次[" + sheet.getSessionId()
                                + "] 切屏 " + sheet.getSwitchCount() + " 次超限（上限 " + limit + "），已强制交卷", 1);
                action = "FORCE_SUBMIT";
            } else {
                updateById(sheet);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("leaveCount", sheet.getSwitchCount());
        m.put("limit", limit);
        m.put("action", action);
        return m;
    }

    // ---------------------------------------------------------------- TAKE-07 交卷（幂等）

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long sheetId) {
        AnswerSheet sheet = requireOwnSheet(sheetId, SecurityUtils.getUserId());
        if (sheet.getSheetStatus() != null && sheet.getSheetStatus() == 2) {
            // 幂等：重复交卷返回首次交卷结果，不产生二次副作用
            return submitResult(sheet);
        }
        ExamSession session = examSessionMapper.selectById(sheet.getSessionId());
        LocalDateTime now = LocalDateTime.now();
        int submitType = 1;
        if (sheet.getServerDeadline() != null && now.isAfter(sheet.getServerDeadline())) {
            submitType = 2;
        } else if (session != null) {
            if (session.getAllowEarlySubmit() != null && session.getAllowEarlySubmit() == 0) {
                throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "本场考试不允许提前交卷");
            }
            int afterMin = session.getEarlySubmitAfterMin() == null ? 0 : session.getEarlySubmitAfterMin();
            if (afterMin > 0 && sheet.getStartTime() != null
                    && now.isBefore(sheet.getStartTime().plusMinutes(afterMin))) {
                throw new BizException(ErrorCode.SESSION_STATUS_INVALID,
                        "开考 " + afterMin + " 分钟后才允许提前交卷");
            }
        }
        submitInternal(sheet, submitType);
        return submitResult(getById(sheetId));
    }

    /**
     * 交卷统一入口（主动/到时/强制/切屏超限）：置状态 → 名单状态 → 判分流水线
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitInternal(AnswerSheet sheet, int submitType) {
        if (sheet.getSheetStatus() != null && sheet.getSheetStatus() == 2) {
            return;
        }
        AnswerSheet update = new AnswerSheet();
        update.setId(sheet.getId());
        update.setSheetStatus(2);
        LocalDateTime submitTime = LocalDateTime.now();
        update.setSubmitTime(submitTime);
        // 答题用时快照(V1.1)= 交卷-进入，超时按场次限时截断，排名"同分按用时升序"以此为准
        if (sheet.getStartTime() != null) {
            long secs = Math.max(0, Duration.between(sheet.getStartTime(), submitTime).getSeconds());
            ExamSession session = examSessionMapper.selectById(sheet.getSessionId());
            if (session != null && session.getDurationMin() != null) {
                secs = Math.min(secs, session.getDurationMin() * 60L);
            }
            update.setDurationSec((int) secs);
        }
        update.setSubmitType(submitType);
        update.setMarkStatus(1);
        updateById(update);
        redisTemplate.delete(SHEET_TOKEN_KEY + sheet.getId());

        ExamCandidate candidateUpdate = new ExamCandidate();
        candidateUpdate.setId(sheet.getCandidateId());
        candidateUpdate.setCandidateStatus(submitType == 3 || submitType == 4 ? 3 : 1);
        examCandidateMapper.updateById(candidateUpdate);

        gradingService.gradeSheet(sheet.getId());
    }

    /** 强制收卷（EXAM-13/END 流转）：在考答卷按 submit_type=3 收卷并写考试异常日志 */
    @Transactional(rollbackFor = Exception.class)
    public int forceSubmitSheets(Long examId, List<Long> userIds, String reason) {
        QueryWrapper<AnswerSheet> qw = new QueryWrapper<>();
        qw.eq("session_id", examId).eq("sheet_status", 1);
        qw.in(userIds != null && !userIds.isEmpty(), "user_id", userIds);
        List<AnswerSheet> sheets = list(qw);
        for (AnswerSheet sheet : sheets) {
            submitInternal(sheet, 3);
            sysLogService.log(3, "EXAM", "FORCE_SUBMIT",
                    "考生[" + sheet.getUserName() + "(" + sheet.getJobNo() + ")] 场次[" + examId + "] 被强制收卷：" + reason, 1);
        }
        return sheets.size();
    }

    private Map<String, Object> submitResult(AnswerSheet sheet) {
        Long unanswered = answerDetailMapper.selectCount(new QueryWrapper<AnswerDetail>()
                .eq("sheet_id", sheet.getId()).isNull("user_answer"));
        AnswerSheet fresh = getById(sheet.getId());
        boolean pureObjectiveDone = fresh.getMarkStatus() != null && fresh.getMarkStatus() >= 4;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sheetId", sheet.getId());
        m.put("submitTime", fresh.getSubmitTime());
        m.put("submitType", fresh.getSubmitType());
        m.put("unansweredCount", unanswered);
        m.put("objectiveAutoScored", true);
        m.put("scoreVisible", pureObjectiveDone);
        m.put("message", pureObjectiveDone ? "客观题已自动判分" : "含主观题，成绩待阅卷发布后可查询");
        return m;
    }

    // ---------------------------------------------------------------- TAKE-08 答卷状态查询（断线续考）

    public Map<String, Object> sheetState(Long sheetId) {
        AnswerSheet sheet = requireOwnSheet(sheetId, SecurityUtils.getUserId());
        // 到时懒收卷：状态查询触发自动交卷判定
        if (sheet.getSheetStatus() != null && sheet.getSheetStatus() == 1
                && sheet.getServerDeadline() != null && LocalDateTime.now().isAfter(sheet.getServerDeadline())) {
            submitInternal(sheet, 2);
            sheet = getById(sheetId);
        }
        ExamSession session = examSessionMapper.selectById(sheet.getSessionId());
        List<AnswerDetail> details = answerDetailMapper.selectList(new QueryWrapper<AnswerDetail>()
                .eq("sheet_id", sheetId).orderByAsc("sort_no"));
        List<Map<String, Object>> answers = new ArrayList<>();
        for (AnswerDetail d : details) {
            if (d.getUserAnswer() != null) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("sheetQuesId", d.getId());
                a.put("answer", d.getUserAnswer());
                answers.add(a);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sheetId", sheet.getId());
        m.put("examId", sheet.getSessionId());
        m.put("submitStatus", submitStatusOf(sheet));
        m.put("serverTime", now);
        m.put("remainingSec", remainSec(sheet, now));
        m.put("deadlineTime", sheet.getServerDeadline());
        m.put("answers", answers);
        m.put("leaveCount", sheet.getSwitchCount());
        m.put("switchScreenLimit", session == null ? null : session.getSwitchLimit());
        return m;
    }

    // ---------------------------------------------------------------- TAKE-09 成绩查询

    public Map<String, Object> result(Long examId) {
        Long userId = SecurityUtils.getUserId();
        ExamCandidate candidate = examCandidateMapper.selectOne(new QueryWrapper<ExamCandidate>()
                .eq("session_id", examId).eq("user_id", userId), false);
        if (candidate == null) {
            throw new BizException(ErrorCode.NOT_IN_CANDIDATE_LIST);
        }
        ExamSession session = examSessionMapper.selectById(examId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考试场次不存在");
        }
        AnswerSheet sheet = getOne(new QueryWrapper<AnswerSheet>()
                .eq("session_id", examId).eq("user_id", userId), false);
        boolean published = session.getSessionStatus() == SessionStatusEnum.SCORED_PUBLISHED.getCode()
                && sheet != null && sheet.getMarkStatus() != null && sheet.getMarkStatus() == 5;
        if (!published) {
            throw new BizException(ErrorCode.SCORE_NOT_PUBLISHED);
        }
        Map<String, Object> snapshot = sheet.getPaperSnapshot() == null ? Map.of() : sheet.getPaperSnapshot();
        Map<Long, Map<String, Object>> questions = GradingService.snapshotQuestions(sheet);
        Object passScore = snapshot.get("passScore");

        List<AnswerDetail> details = answerDetailMapper.selectList(new QueryWrapper<AnswerDetail>()
                .eq("sheet_id", sheet.getId()).orderByAsc("sort_no"));
        List<Map<String, Object>> detailList = new ArrayList<>();
        for (AnswerDetail d : details) {
            Map<String, Object> q = questions.get(d.getQuesId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", d.getSortNo());
            item.put("sheetQuesId", d.getId());
            item.put("quesType", QuesTypeEnum.toApi(d.getQuesType()));
            item.put("stem", q == null ? null : q.get("stem"));
            item.put("options", sanitizeOptions(q == null ? null : q.get("options")));
            item.put("myAnswer", d.getUserAnswer());
            item.put("correctAnswer", q == null ? null : q.get("answer"));
            item.put("isCorrect", d.getIsRight() != null && d.getIsRight() == 1);
            item.put("isRight", d.getIsRight());
            item.put("gainedScore", d.getScore());
            item.put("fullScore", d.getQuesScore());
            item.put("analysis", q == null ? null : q.get("analysis"));
            detailList.add(item);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examId", examId);
        m.put("examName", session.getSessionName());
        m.put("scorePublished", true);
        m.put("totalScore", snapshot.get("totalScore"));
        m.put("myScore", sheet.getTotalScore());
        m.put("grade", sheet.getGradeName());
        m.put("passed", passScore != null && sheet.getTotalScore() != null
                && sheet.getTotalScore().compareTo(RandomStrategy.asBigDecimal(passScore)) >= 0);
        m.put("objectiveScore", sheet.getObjectiveScore());
        m.put("subjectiveScore", sheet.getSubjectiveScore());
        m.put("showAnalysis", true);
        m.put("details", detailList);
        return m;
    }

    // ---------------------------------------------------------------- 内部工具

    private AnswerSheet requireOwnSheet(Long sheetId, Long userId) {
        AnswerSheet sheet = getById(sheetId);
        if (sheet == null || !sheet.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.SHEET_NOT_FOUND);
        }
        return sheet;
    }

    /** 校验答卷属于本人且答题中（50006/50003）；到时先懒收卷 */
    private AnswerSheet requireOwnActiveSheet(Long sheetId) {
        AnswerSheet sheet = requireOwnSheet(sheetId, SecurityUtils.getUserId());
        if (sheet.getSheetStatus() != null && sheet.getSheetStatus() == 1
                && sheet.getServerDeadline() != null && LocalDateTime.now().isAfter(sheet.getServerDeadline())) {
            submitInternal(sheet, 2);
            sheet = getById(sheetId);
        }
        if (sheet.getSheetStatus() == null || sheet.getSheetStatus() != 1) {
            throw new BizException(ErrorCode.ALREADY_SUBMITTED);
        }
        return sheet;
    }

    private void cacheToken(AnswerSheet sheet) {
        if (sheet.getSessionToken() == null || sheet.getServerDeadline() == null) {
            return;
        }
        long ttl = Math.max(300, Duration.between(LocalDateTime.now(), sheet.getServerDeadline()).getSeconds());
        redisTemplate.opsForValue().set(SHEET_TOKEN_KEY + sheet.getId(), sheet.getSessionToken(),
                Duration.ofSeconds(ttl));
    }

    private static LocalDateTime deadlineOf(ExamSession session, LocalDateTime enterTime) {
        LocalDateTime byDuration = enterTime.plusMinutes(session.getDurationMin());
        return byDuration.isBefore(session.getEndTime()) ? byDuration : session.getEndTime();
    }

    private static long remainSec(AnswerSheet sheet, LocalDateTime now) {
        if (sheet == null || sheet.getServerDeadline() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(now, sheet.getServerDeadline()).getSeconds());
    }

    static String submitStatusOf(AnswerSheet sheet) {
        if (sheet.getSheetStatus() == null || sheet.getSheetStatus() == 1) {
            return "IN_EXAM";
        }
        if (sheet.getSubmitType() != null && (sheet.getSubmitType() == 3 || sheet.getSubmitType() == 4)) {
            return "FORCED";
        }
        return "SUBMITTED";
    }
}
