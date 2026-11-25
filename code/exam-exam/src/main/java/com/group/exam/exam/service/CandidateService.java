package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.CandidateScopeReq;
import com.group.exam.exam.entity.AnswerSheet;
import com.group.exam.exam.entity.ExamCandidate;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.enums.SessionStatusEnum;
import com.group.exam.exam.mapper.AnswerSheetMapper;
import com.group.exam.exam.mapper.ExamCandidateMapper;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.OrgUserService;
import com.group.exam.system.service.SysLogService;
import com.group.exam.system.service.SysNoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考生名单（EXAM-07~11）：范围生成快照/追加/剔除/分页/通知
 */
@Service
public class CandidateService extends ServiceImpl<ExamCandidateMapper, ExamCandidate> {

    private final ExamSessionService examSessionService;
    private final AnswerSheetMapper answerSheetMapper;
    private final OrgUserService orgUserService;
    private final OrgUnitService orgUnitService;
    private final SysNoticeService sysNoticeService;
    private final SysLogService sysLogService;

    public CandidateService(ExamSessionService examSessionService, AnswerSheetMapper answerSheetMapper,
                            OrgUserService orgUserService, OrgUnitService orgUnitService,
                            SysNoticeService sysNoticeService, SysLogService sysLogService) {
        this.examSessionService = examSessionService;
        this.answerSheetMapper = answerSheetMapper;
        this.orgUserService = orgUserService;
        this.orgUnitService = orgUnitService;
        this.sysNoticeService = sysNoticeService;
        this.sysLogService = sysLogService;
    }

    // ---------------------------------------------------------------- EXAM-07 按组织范围生成名单（全量重建，幂等）

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> buildByScope(Long examId, CandidateScopeReq req) {
        ExamSession session = examSessionService.requireInScope(examId);
        if (req.getScopes() == null || req.getScopes().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "scopes 不能为空");
        }
        long sheetCount = answerSheetMapper.selectCount(new QueryWrapper<AnswerSheet>().eq("session_id", examId));
        if (sheetCount > 0) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "已有考生进入测试，禁止重建名单");
        }
        // 逐范围展开在册人员（含/不含下级），越权 10004
        Map<Long, OrgUser> users = new LinkedHashMap<>();
        for (CandidateScopeReq.ScopeItem scope : req.getScopes()) {
            if (scope.getOrgId() == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "scopes 条目缺少 orgId");
            }
            OrgUnit unit = orgUnitService.getUnit(scope.getOrgId());
            if (unit == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "组织单位不存在: " + scope.getOrgId());
            }
            SecurityUtils.checkOrgScope(unit.getPath());
            boolean withChildren = scope.getIncludeSub() == null || scope.getIncludeSub();
            for (OrgUser u : orgUserService.listActiveByOrgPaths(List.of(unit.getPath()), withChildren)) {
                users.putIfAbsent(u.getId(), u);
            }
        }
        // 范围解析为空时禁止清空原名单（防止误选无在册人员的单位导致名单被清空）
        if (users.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所选组织范围内无在册人员，名单未变更");
        }
        baseMapper.physicalDeleteBySessionId(examId);
        Long operatorId = SecurityUtils.getUserId();
        Map<Long, String> orgNames = orgUnitService.mapUnitNames(
                users.values().stream().map(OrgUser::getOrgId).collect(Collectors.toSet()));
        for (OrgUser u : users.values()) {
            ExamCandidate c = new ExamCandidate();
            c.setSessionId(examId);
            c.setUserId(u.getId());
            c.setUserName(u.getUserName());
            c.setJobNo(u.getJobNo());
            c.setOrgId(u.getOrgId());
            c.setOrgPath(u.getOrgPath());
            c.setOrgName(orgNames.get(u.getOrgId()));
            c.setCandidateStatus(0);
            c.setSource(1);
            c.setNotifyStatus(0);
            c.setCreateBy(operatorId);
            save(c);
        }
        sysLogService.log(1, "EXAM", "生成考生名单",
                "场次[" + session.getSessionName() + "] 按组织范围生成名单 " + users.size() + " 人", 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", examId);
        result.put("candidateCount", users.size());
        result.put("snapshotTime", LocalDateTime.now());
        return result;
    }

    // ---------------------------------------------------------------- EXAM-08 名单分页

    public PageResult<Map<String, Object>> page(Long examId, String keyword, String sheetStatus,
                                                long pageNum, long pageSize) {
        examSessionService.requireInScope(examId);
        QueryWrapper<ExamCandidate> qw = new QueryWrapper<>();
        qw.eq("session_id", examId);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("user_name", keyword.trim()).or().like("job_no", keyword.trim()));
        }
        applySheetStatusFilter(qw, examId, sheetStatus);
        qw.orderByAsc("id");
        Page<ExamCandidate> page = page(new Page<>(pageNum, pageSize), qw);

        List<Long> userIds = page.getRecords().stream().map(ExamCandidate::getUserId).toList();
        Map<Long, AnswerSheet> sheetByUser = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (AnswerSheet s : answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                    .eq("session_id", examId).in("user_id", userIds))) {
                sheetByUser.put(s.getUserId(), s);
            }
        }
        return PageResult.of(page).map(c -> {
            AnswerSheet sheet = sheetByUser.get(c.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("candidateId", c.getId());
            m.put("userId", c.getUserId());
            m.put("name", c.getUserName());
            m.put("jobNo", c.getJobNo());
            m.put("orgNameSnapshot", c.getOrgName());
            m.put("candidateStatus", c.getCandidateStatus());
            m.put("sheetId", sheet == null ? null : sheet.getId());
            m.put("sheetStatus", sheetStatusOf(c, sheet));
            m.put("enterTime", sheet == null ? null : sheet.getStartTime());
            m.put("submitTime", sheet == null ? null : sheet.getSubmitTime());
            m.put("objectiveScore", sheet == null ? null : sheet.getObjectiveScore());
            m.put("totalScore", sheet == null ? null : sheet.getTotalScore());
            m.put("grade", sheet == null ? null : sheet.getGradeName());
            return m;
        });
    }

    private void applySheetStatusFilter(QueryWrapper<ExamCandidate> qw, Long examId, String sheetStatus) {
        if (!StringUtils.hasText(sheetStatus)) {
            return;
        }
        switch (sheetStatus.trim().toUpperCase()) {
            case "NOT_ENTERED" -> qw.notInSql("user_id",
                    "SELECT user_id FROM t_answer_sheet WHERE session_id = " + examId);
            case "IN_EXAM" -> qw.inSql("user_id",
                    "SELECT user_id FROM t_answer_sheet WHERE session_id = " + examId + " AND sheet_status = 1");
            case "SUBMITTED" -> qw.inSql("user_id",
                    "SELECT user_id FROM t_answer_sheet WHERE session_id = " + examId
                            + " AND sheet_status = 2 AND submit_type IN (1,2)");
            case "FORCED" -> qw.inSql("user_id",
                    "SELECT user_id FROM t_answer_sheet WHERE session_id = " + examId
                            + " AND sheet_status = 2 AND submit_type IN (3,4)");
            case "ABSENT" -> qw.eq("candidate_status", 2);
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法 sheetStatus: " + sheetStatus);
        }
    }

    static String sheetStatusOf(ExamCandidate c, AnswerSheet sheet) {
        if (sheet == null) {
            return c.getCandidateStatus() != null && c.getCandidateStatus() == 2 ? "ABSENT" : "NOT_ENTERED";
        }
        if (sheet.getSheetStatus() != null && sheet.getSheetStatus() == 1) {
            return "IN_EXAM";
        }
        if (sheet.getSubmitType() != null && (sheet.getSubmitType() == 3 || sheet.getSubmitType() == 4)) {
            return "FORCED";
        }
        return "SUBMITTED";
    }

    // ---------------------------------------------------------------- EXAM-09 追加考生

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> add(Long examId, List<Long> userIds) {
        ExamSession session = examSessionService.requireInScope(examId);
        requireListEditable(session);
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "userIds 不能为空");
        }
        Set<Long> distinct = new LinkedHashSet<>(userIds);
        Map<Long, OrgUser> users = orgUserService.listByIds(distinct).stream()
                .collect(Collectors.toMap(OrgUser::getId, u -> u));
        Map<Long, String> orgNames = orgUnitService.mapUnitNames(
                users.values().stream().map(OrgUser::getOrgId).collect(Collectors.toSet()));
        Long operatorId = SecurityUtils.getUserId();

        int added = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (Long userId : distinct) {
            OrgUser u = users.get(userId);
            if (u == null || (u.getAccountStatus() != null && u.getAccountStatus() == 0)) {
                skipped.add(skipItem(userId, "人员不存在或已停用"));
                continue;
            }
            if (!SecurityUtils.inOrgScope(u.getOrgPath())) {
                skipped.add(skipItem(userId, "人员不在数据权限范围内"));
                continue;
            }
            ExamCandidate existing = baseMapper.selectIncludeDeleted(examId, userId);
            if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
                skipped.add(skipItem(userId, "已在名单内"));
                continue;
            }
            if (existing != null) {
                // 曾被剔除：复活该行（uk(session_id,user_id) 约束下不可重复插入）
                existing.setDeleted(0);
                existing.setUserName(u.getUserName());
                existing.setJobNo(u.getJobNo());
                existing.setOrgId(u.getOrgId());
                existing.setOrgPath(u.getOrgPath());
                existing.setOrgName(orgNames.get(u.getOrgId()));
                existing.setCandidateStatus(0);
                existing.setSource(2);
                existing.setNotifyStatus(0);
                existing.setUpdateBy(operatorId);
                baseMapper.revive(existing);
            } else {
                ExamCandidate c = new ExamCandidate();
                c.setSessionId(examId);
                c.setUserId(u.getId());
                c.setUserName(u.getUserName());
                c.setJobNo(u.getJobNo());
                c.setOrgId(u.getOrgId());
                c.setOrgPath(u.getOrgPath());
                c.setOrgName(orgNames.get(u.getOrgId()));
                c.setCandidateStatus(0);
                c.setSource(2);
                c.setNotifyStatus(0);
                c.setCreateBy(operatorId);
                save(c);
            }
            added++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addedCount", added);
        result.put("skipped", skipped);
        return result;
    }

    // ---------------------------------------------------------------- EXAM-10 剔除考生（deleted=1）

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> remove(Long examId, List<Long> userIds) {
        ExamSession session = examSessionService.requireInScope(examId);
        requireListEditable(session);
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "userIds 不能为空");
        }
        Set<Long> sheetUsers = answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                        .select("user_id").eq("session_id", examId).in("user_id", userIds))
                .stream().map(AnswerSheet::getUserId).collect(Collectors.toSet());
        int removed = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (sheetUsers.contains(userId)) {
                skipped.add(skipItem(userId, "已进入测试，不可剔除，可单独强制收卷"));
                continue;
            }
            ExamCandidate existing = getOne(new QueryWrapper<ExamCandidate>()
                    .eq("session_id", examId).eq("user_id", userId), false);
            if (existing == null) {
                skipped.add(skipItem(userId, "不在名单内"));
                continue;
            }
            removeById(existing.getId());
            removed++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("removedCount", removed);
        result.put("skipped", skipped);
        return result;
    }

    // ---------------------------------------------------------------- EXAM-11 推送测试通知

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> notify(Long examId, String message, List<String> channels, List<Long> userIds) {
        ExamSession session = examSessionService.requireInScope(examId);
        if (session.getSessionStatus() == SessionStatusEnum.CANCELED.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "场次已取消，不可推送通知");
        }
        List<ExamCandidate> candidates = list(new QueryWrapper<ExamCandidate>().eq("session_id", examId));
        if (candidates.isEmpty()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID,
                    "考生名单为空，请先在「考生名单」页签按组织生成名单或追加考生");
        }
        List<Long> receiverIds;
        if (userIds != null && !userIds.isEmpty()) {
            Set<Long> inList = candidates.stream().map(ExamCandidate::getUserId).collect(Collectors.toSet());
            receiverIds = new LinkedHashSet<>(userIds).stream().filter(inList::contains).toList();
            if (receiverIds.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "所选考生不在本场名单内");
            }
        } else {
            receiverIds = candidates.stream().map(ExamCandidate::getUserId).toList();
        }
        String title = "测试通知：" + session.getSessionName();
        String content = StringUtils.hasText(message) ? message
                : "请按时参加测试[" + session.getSessionName() + "]";
        // channels 目前仅支持 INBOX（站内消息），SMS/WECOM 预留忽略
        sysNoticeService.sendBatch(1, title, content, "session", examId, receiverIds);
        update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ExamCandidate>()
                .eq("session_id", examId).in("user_id", receiverIds).set("notify_status", 1));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sentCount", receiverIds.size());
        return result;
    }

    // ---------------------------------------------------------------- 内部工具

    private void requireListEditable(ExamSession session) {
        int status = session.getSessionStatus();
        if (status == SessionStatusEnum.ENDED.getCode()
                || status == SessionStatusEnum.SCORED_PUBLISHED.getCode()
                || status == SessionStatusEnum.CANCELED.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "场次当前状态不允许调整名单");
        }
    }

    private Map<String, Object> skipItem(Long userId, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("reason", reason);
        return m;
    }
}
