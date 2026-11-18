package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.entity.AnswerDetail;
import com.group.exam.exam.entity.AnswerSheet;
import com.group.exam.exam.entity.ExamCandidate;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.entity.MarkTask;
import com.group.exam.exam.entity.StatExamUnit;
import com.group.exam.exam.enums.SessionStatusEnum;
import com.group.exam.exam.mapper.AnswerDetailMapper;
import com.group.exam.exam.mapper.AnswerSheetMapper;
import com.group.exam.exam.mapper.ExamCandidateMapper;
import com.group.exam.exam.mapper.ExamSessionMapper;
import com.group.exam.exam.mapper.MarkTaskMapper;
import com.group.exam.exam.mapper.StatExamUnitMapper;
import com.group.exam.exam.support.RandomStrategy;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.SysConfigService;
import com.group.exam.system.service.SysLogService;
import com.group.exam.system.service.SysNoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 成绩合成发布（MARK-07）：校验 → 4→5 → 场次发布 → 通知 → t_stat_exam_unit 预聚合快照
 */
@Service
public class ScorePublishService extends ServiceImpl<ExamSessionMapper, ExamSession> {

    private final AnswerSheetMapper answerSheetMapper;
    private final AnswerDetailMapper answerDetailMapper;
    private final ExamCandidateMapper examCandidateMapper;
    private final MarkTaskMapper markTaskMapper;
    private final StatExamUnitMapper statExamUnitMapper;
    private final SheetService sheetService;
    private final GradingService gradingService;
    private final SysNoticeService sysNoticeService;
    private final SysConfigService sysConfigService;
    private final OrgUnitService orgUnitService;
    private final SysLogService sysLogService;

    public ScorePublishService(AnswerSheetMapper answerSheetMapper, AnswerDetailMapper answerDetailMapper,
                               ExamCandidateMapper examCandidateMapper, MarkTaskMapper markTaskMapper,
                               StatExamUnitMapper statExamUnitMapper, SheetService sheetService,
                               GradingService gradingService, SysNoticeService sysNoticeService,
                               SysConfigService sysConfigService, OrgUnitService orgUnitService,
                               SysLogService sysLogService) {
        this.answerSheetMapper = answerSheetMapper;
        this.answerDetailMapper = answerDetailMapper;
        this.examCandidateMapper = examCandidateMapper;
        this.markTaskMapper = markTaskMapper;
        this.statExamUnitMapper = statExamUnitMapper;
        this.sheetService = sheetService;
        this.gradingService = gradingService;
        this.sysNoticeService = sysNoticeService;
        this.sysConfigService = sysConfigService;
        this.orgUnitService = orgUnitService;
        this.sysLogService = sysLogService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publish(Long examId) {
        ExamSession session = getById(examId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考试场次不存在: " + examId);
        }
        SecurityUtils.checkOrgScope(session.getCreateOrgPath());
        LoginUser user = SecurityUtils.getLoginUser();
        boolean privileged = user.hasRole("SUPER_ADMIN") || user.hasRole("HQ_ADMIN");
        if (!privileged && (session.getCreateBy() == null || !session.getCreateBy().equals(user.getUserId()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅总管/超管或场次创建方可发布成绩");
        }
        if (session.getSessionStatus() == SessionStatusEnum.SCORED_PUBLISHED.getCode()) {
            // 幂等：重复发布返回首次发布结果口径
            return publishResult(session);
        }
        if (session.getSessionStatus() != SessionStatusEnum.ENDED.getCode()
                && session.getSessionStatus() != SessionStatusEnum.IN_PROGRESS.getCode()) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID, "场次当前状态不允许发布成绩");
        }
        // 仍在答题中的答卷：到时/强制收卷后纳入判分
        sheetService.forceSubmitSheets(examId, null, "成绩发布前统一收卷");
        // 兜底重跑合成（复核/阅卷刚完成的场景）
        List<AnswerSheet> submitted = answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                .eq("session_id", examId).eq("sheet_status", 2));
        for (AnswerSheet sheet : submitted) {
            if (sheet.getMarkStatus() != null && sheet.getMarkStatus() >= 1 && sheet.getMarkStatus() < 4) {
                gradingService.trySynthesize(sheet.getId());
            }
        }
        // 前置校验：全部答卷已合成、复核队列清空、无待阅任务
        List<AnswerSheet> fresh = answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                .eq("session_id", examId).eq("sheet_status", 2));
        long notGraded = fresh.stream()
                .filter(s -> s.getMarkStatus() == null || s.getMarkStatus() < 4).count();
        Long pendingTasks = markTaskMapper.selectCount(new QueryWrapper<MarkTask>()
                .eq("session_id", examId).eq("mark_status", 0));
        Long pendingReviews = answerDetailMapper.selectCount(new QueryWrapper<AnswerDetail>()
                .eq("session_id", examId).eq("mark_status", 3));
        if (notGraded > 0 || (pendingTasks != null && pendingTasks > 0) || (pendingReviews != null && pendingReviews > 0)) {
            throw new BizException(ErrorCode.SESSION_STATUS_INVALID,
                    "存在未完成项：未合成答卷 " + notGraded + " 份，待阅任务 " + pendingTasks
                            + " 个，待复核 " + pendingReviews + " 条");
        }
        // 批量 4→5
        answerSheetMapper.update(null, new UpdateWrapper<AnswerSheet>()
                .eq("session_id", examId).eq("mark_status", 4).set("mark_status", 5));
        // 名单状态：已交卷 1 / 缺考 2（强制收卷 3 保留）
        List<ExamCandidate> candidates = examCandidateMapper.selectList(
                new QueryWrapper<ExamCandidate>().eq("session_id", examId));
        Map<Long, AnswerSheet> sheetByUser = fresh.stream()
                .collect(Collectors.toMap(AnswerSheet::getUserId, s -> s, (a, b) -> a));
        for (ExamCandidate c : candidates) {
            AnswerSheet sheet = sheetByUser.get(c.getUserId());
            int target = sheet == null ? 2
                    : (c.getCandidateStatus() != null && c.getCandidateStatus() == 3 ? 3 : 1);
            if (c.getCandidateStatus() == null || c.getCandidateStatus() != target) {
                ExamCandidate update = new ExamCandidate();
                update.setId(c.getId());
                update.setCandidateStatus(target);
                examCandidateMapper.updateById(update);
                c.setCandidateStatus(target);
            }
        }
        // 场次发布
        session.setSessionStatus(SessionStatusEnum.SCORED_PUBLISHED.getCode());
        session.setScorePublishTime(LocalDateTime.now());
        session.setUpdateBy(user.getUserId());
        updateById(session);
        // 成绩发布通知（noticeType=2）
        sysNoticeService.sendBatch(2, "成绩发布：" + session.getSessionName(),
                "考试[" + session.getSessionName() + "]成绩已发布，请前往考生端查询",
                "session", examId, candidates.stream().map(ExamCandidate::getUserId).toList());
        // 场次×单位预聚合统计快照
        rebuildStatSnapshot(session, candidates, fresh);
        sysLogService.log(1, "MARK", "成绩发布",
                "场次[" + session.getSessionName() + "] 发布成绩 " + fresh.size() + " 份", 1);
        return publishResult(session);
    }

    // ---------------------------------------------------------------- 统计快照（exam-stat 模块读取）

    private void rebuildStatSnapshot(ExamSession session, List<ExamCandidate> candidates, List<AnswerSheet> sheets) {
        Map<Long, List<ExamCandidate>> byOrg = candidates.stream()
                .collect(Collectors.groupingBy(ExamCandidate::getOrgId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, AnswerSheet> sheetByUser = sheets.stream()
                .collect(Collectors.toMap(AnswerSheet::getUserId, s -> s, (a, b) -> a));
        BigDecimal passScore = session.getPaperStrategySnapshot() == null
                ? null : RandomStrategy.asBigDecimal(session.getPaperStrategySnapshot().get("passScore"));
        List<int[]> segments = parseSegments(sysConfigService.getValue("exam.scoreSegmentDefault"));

        for (Map.Entry<Long, List<ExamCandidate>> entry : byOrg.entrySet()) {
            Long orgId = entry.getKey();
            List<ExamCandidate> group = entry.getValue();
            List<AnswerSheet> orgSheets = group.stream()
                    .map(c -> sheetByUser.get(c.getUserId()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            StatExamUnit stat = new StatExamUnit();
            stat.setSessionId(session.getId());
            stat.setOrgId(orgId);
            ExamCandidate first = group.get(0);
            stat.setOrgPath(first.getOrgPath());
            stat.setOrgName(first.getOrgName());
            OrgUnit unit = orgUnitService.getUnit(orgId);
            stat.setOrgLevel(unit != null && unit.getLevel() != null ? unit.getLevel()
                    : (int) first.getOrgPath().chars().filter(ch -> ch == '/').count() - 1);
            stat.setShouldCount(group.size());
            stat.setActualCount(orgSheets.size());
            stat.setAbsentCount(group.size() - orgSheets.size());

            List<BigDecimal> scores = orgSheets.stream().map(AnswerSheet::getTotalScore)
                    .filter(java.util.Objects::nonNull).toList();
            if (!scores.isEmpty()) {
                BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                stat.setAvgScore(sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP));
                stat.setMaxScore(scores.stream().max(BigDecimal::compareTo).orElse(null));
                stat.setMinScore(scores.stream().min(BigDecimal::compareTo).orElse(null));
            }
            int passCount = passScore == null ? 0
                    : (int) scores.stream().filter(s -> s.compareTo(passScore) >= 0).count();
            stat.setPassCount(passCount);
            stat.setPassRate(scores.isEmpty() ? BigDecimal.ZERO
                    : BigDecimal.valueOf(passCount * 100.0 / scores.size()).setScale(2, RoundingMode.HALF_UP));

            Map<String, Object> gradeDist = new TreeMap<>();
            for (AnswerSheet s : orgSheets) {
                if (s.getGradeName() != null) {
                    gradeDist.merge(s.getGradeName(), 1, (a, b) -> (Integer) a + (Integer) b);
                }
            }
            stat.setGradeDist(gradeDist);

            Map<String, Object> segmentDist = new LinkedHashMap<>();
            String segConfig = sysConfigService.getValue("exam.scoreSegmentDefault");
            String[] segNames = segConfig == null || segConfig.isBlank()
                    ? new String[]{"0-59", "60-69", "70-79", "80-89", "90-100"} : segConfig.split(",");
            for (String name : segNames) {
                segmentDist.put(name.trim(), 0);
            }
            for (BigDecimal score : scores) {
                for (int i = 0; i < segments.size(); i++) {
                    int[] seg = segments.get(i);
                    double v = score.doubleValue();
                    if (v >= seg[0] && v <= seg[1]) {
                        String name = segNames[i].trim();
                        segmentDist.put(name, (Integer) segmentDist.get(name) + 1);
                        break;
                    }
                }
            }
            stat.setSegmentDist(segmentDist);
            stat.setStatTime(LocalDateTime.now());

            StatExamUnit existing = statExamUnitMapper.selectOne(new QueryWrapper<StatExamUnit>()
                    .eq("session_id", session.getId()).eq("org_id", orgId), false);
            if (existing == null) {
                statExamUnitMapper.insert(stat);
            } else {
                stat.setId(existing.getId());
                statExamUnitMapper.updateById(stat);
            }
        }
    }

    /** 解析分数段配置 "0-59,60-69,..." 为闭区间数组 */
    private static List<int[]> parseSegments(String config) {
        String text = (config == null || config.isBlank()) ? "0-59,60-69,70-79,80-89,90-100" : config;
        List<int[]> segments = new ArrayList<>();
        for (String part : text.split(",")) {
            String[] bounds = part.trim().split("-");
            if (bounds.length == 2) {
                try {
                    segments.add(new int[]{Integer.parseInt(bounds[0].trim()), Integer.parseInt(bounds[1].trim())});
                } catch (NumberFormatException ignored) {
                    // 非法分段忽略
                }
            }
        }
        return segments;
    }

    private Map<String, Object> publishResult(ExamSession session) {
        List<AnswerSheet> published = answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                .eq("session_id", session.getId()).eq("mark_status", 5));
        List<BigDecimal> scores = published.stream().map(AnswerSheet::getTotalScore)
                .filter(java.util.Objects::nonNull).toList();
        BigDecimal avg = scores.isEmpty() ? null
                : scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        BigDecimal passScore = session.getPaperStrategySnapshot() == null
                ? null : RandomStrategy.asBigDecimal(session.getPaperStrategySnapshot().get("passScore"));
        double passRate = scores.isEmpty() || passScore == null ? 0
                : scores.stream().filter(s -> s.compareTo(passScore) >= 0).count() * 1.0 / scores.size();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examId", session.getId());
        m.put("publishedCount", published.size());
        m.put("avgScore", avg);
        m.put("passRate", BigDecimal.valueOf(passRate).setScale(4, RoundingMode.HALF_UP));
        return m;
    }
}
