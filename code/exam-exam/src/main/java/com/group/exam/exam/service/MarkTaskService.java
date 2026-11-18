package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.MarkScoresReq;
import com.group.exam.exam.dto.ReviewSubmitReq;
import com.group.exam.exam.entity.AnswerDetail;
import com.group.exam.exam.entity.AnswerSheet;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.entity.MarkTask;
import com.group.exam.exam.mapper.AnswerDetailMapper;
import com.group.exam.exam.mapper.AnswerSheetMapper;
import com.group.exam.exam.mapper.ExamSessionMapper;
import com.group.exam.exam.mapper.MarkTaskMapper;
import com.group.exam.question.enums.QuesTypeEnum;
import com.group.exam.system.service.SysLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 阅卷任务与人工复核（MARK-01~06）
 *
 * <p>任务粒度：t_mark_task 一行 = 场次 × 答卷 × 题（round_no=1 一评）。
 * MARK-03 按"场次 × 题 × 阅卷人"分组展开待阅明细，匿名化展示。
 */
@Service
public class MarkTaskService extends ServiceImpl<MarkTaskMapper, MarkTask> {

    private final AnswerSheetMapper answerSheetMapper;
    private final AnswerDetailMapper answerDetailMapper;
    private final ExamSessionMapper examSessionMapper;
    private final GradingService gradingService;
    private final SysLogService sysLogService;

    public MarkTaskService(AnswerSheetMapper answerSheetMapper, AnswerDetailMapper answerDetailMapper,
                           ExamSessionMapper examSessionMapper, GradingService gradingService,
                           SysLogService sysLogService) {
        this.answerSheetMapper = answerSheetMapper;
        this.answerDetailMapper = answerDetailMapper;
        this.examSessionMapper = examSessionMapper;
        this.gradingService = gradingService;
        this.sysLogService = sysLogService;
    }

    // ---------------------------------------------------------------- MARK-01 任务列表

    public PageResult<Map<String, Object>> pageTasks(Long examId, String status, long pageNum, long pageSize) {
        LoginUser user = SecurityUtils.getLoginUser();
        QueryWrapper<MarkTask> qw = new QueryWrapper<>();
        // 阅卷教师仅见分派给本人的任务；管理员按数据权限见本组织场次全部任务
        if (!isAdmin(user)) {
            qw.eq("marker_id", user.getUserId());
        } else {
            String prefix = SecurityUtils.dataScopePathPrefix();
            if (prefix != null) {
                qw.inSql("session_id", "SELECT id FROM t_exam_session WHERE deleted = 0 AND create_org_path LIKE '"
                        + prefix.replace("'", "''") + "%'");
            }
        }
        qw.eq(examId != null, "session_id", examId);
        if (status != null && !status.isBlank()) {
            switch (status.trim().toUpperCase()) {
                case "PENDING", "DOING" -> qw.eq("mark_status", 0);
                case "DONE" -> qw.in("mark_status", 1, 3);
                default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法任务状态: " + status);
            }
        }
        qw.orderByAsc("mark_status").orderByAsc("id");
        Page<MarkTask> page = page(new Page<>(pageNum, pageSize), qw);

        Map<Long, String> examNames = examNamesOf(page.getRecords().stream().map(MarkTask::getSessionId).toList());
        // 截止时间：取场次结束时间（阅卷在测试结束后截止）
        Map<Long, LocalDateTime> sessionEndTimes = sessionEndTimesOf(page.getRecords().stream().map(MarkTask::getSessionId).toList());
        // 题型：t_mark_task 无 ques_type 列，按 detailId 批量取答题明细的 ques_type
        Map<Long, Integer> quesTypes = new HashMap<>();
        List<Long> detailIds = page.getRecords().stream().map(MarkTask::getDetailId).distinct().toList();
        if (!detailIds.isEmpty()) {
            for (AnswerDetail d : answerDetailMapper.selectList(new QueryWrapper<AnswerDetail>()
                    .select("id", "ques_type").in("id", detailIds))) {
                quesTypes.put(d.getId(), d.getQuesType());
            }
        }
        return PageResult.of(page).map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("examId", t.getSessionId());
            m.put("examName", examNames.get(t.getSessionId()));
            m.put("sheetId", t.getSheetId());
            m.put("detailId", t.getDetailId());
            m.put("quesId", t.getQuesId());
            m.put("quesType", QuesTypeEnum.toApi(quesTypes.get(t.getDetailId())));
            m.put("markerId", t.getMarkerId());
            m.put("markerName", t.getMarkerName());
            m.put("myRole", t.getMarkerId() != null && t.getMarkerId().equals(user.getUserId()) ? "OWNER" : "UNCLAIMED");
            m.put("status", t.getMarkStatus() != null && t.getMarkStatus() != 0 ? "DONE" : "PENDING");
            m.put("score", t.getScore());
            m.put("assignTime", t.getAssignTime());
            m.put("markTime", t.getMarkTime());
            m.put("deadline", sessionEndTimes.get(t.getSessionId()));
            return m;
        });
    }

    // ---------------------------------------------------------------- MARK-02 领取任务

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> claim(Long taskId) {
        LoginUser user = SecurityUtils.getLoginUser();
        MarkTask task = getById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "阅卷任务不存在: " + taskId);
        }
        if (task.getMarkStatus() != null && task.getMarkStatus() != 0) {
            throw new BizException(ErrorCode.MARK_TASK_INVALID, "任务已完成，不可领取");
        }
        if (task.getMarkerId() != null && !task.getMarkerId().equals(user.getUserId())) {
            throw new BizException(ErrorCode.MARK_TASK_INVALID, "任务已被分配给 " + task.getMarkerName());
        }
        // 分配给本人：领取即确认（乐观锁防并发）
        int updated = baseMapper.claim(taskId, user.getUserId(), user.getUserName());
        if (updated == 0) {
            throw new BizException(ErrorCode.MARK_TASK_INVALID);
        }
        sysLogService.log(1, "MARK", "领取阅卷任务", "领取任务 " + taskId, 1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", taskId);
        m.put("status", "DOING");
        return m;
    }

    // ---------------------------------------------------------------- MARK-03 待阅题目列表（匿名化）

    public PageResult<Map<String, Object>> taskItems(Long taskId, Boolean marked, long pageNum, long pageSize) {
        LoginUser user = SecurityUtils.getLoginUser();
        MarkTask task = getById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "阅卷任务不存在: " + taskId);
        }
        if (!isAdmin(user) && (task.getMarkerId() == null || !task.getMarkerId().equals(user.getUserId()))) {
            throw new BizException(ErrorCode.MARK_TASK_INVALID);
        }
        // 同场次 × 同题 × 同阅卷人 的任务集合作为待阅列表
        QueryWrapper<MarkTask> qw = new QueryWrapper<>();
        qw.eq("session_id", task.getSessionId()).eq("ques_id", task.getQuesId());
        if (!isAdmin(user)) {
            qw.eq("marker_id", user.getUserId());
        }
        if (marked != null) {
            if (marked) {
                qw.in("mark_status", 1, 3);
            } else {
                qw.eq("mark_status", 0);
            }
        }
        qw.orderByAsc("id");
        Page<MarkTask> page = page(new Page<>(pageNum, pageSize), qw);

        List<Long> detailIds = page.getRecords().stream().map(MarkTask::getDetailId).toList();
        Map<Long, AnswerDetail> detailMap = detailIds.isEmpty() ? Map.of()
                : answerDetailMapper.selectBatchIds(detailIds).stream()
                .collect(Collectors.toMap(AnswerDetail::getId, Function.identity()));
        Map<Long, AnswerSheet> sheetMap = new HashMap<>();
        List<Long> sheetIds = page.getRecords().stream().map(MarkTask::getSheetId).toList();
        if (!sheetIds.isEmpty()) {
            sheetMap = answerSheetMapper.selectBatchIds(sheetIds).stream()
                    .collect(Collectors.toMap(AnswerSheet::getId, Function.identity()));
        }
        long offset = (pageNum - 1) * pageSize;
        int index = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (MarkTask t : page.getRecords()) {
            AnswerDetail detail = detailMap.get(t.getDetailId());
            AnswerSheet sheet = sheetMap.get(t.getSheetId());
            Map<String, Object> q = sheet == null ? null
                    : GradingService.snapshotQuestions(sheet).get(t.getQuesId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("detailId", t.getDetailId());
            m.put("taskId", t.getId());
            m.put("sheetId", t.getSheetId());
            m.put("anonymousNo", "卷-" + String.format("%03d", offset + (++index)));
            m.put("stem", q == null ? null : q.get("stem"));
            Object reference = null;
            Object keyPoints = null;
            if (q != null && q.get("answer") instanceof Map<?, ?> answer) {
                reference = answer.get("reference");
                keyPoints = answer.get("points") != null ? answer.get("points") : answer.get("blanks");
            }
            m.put("reference", reference);
            m.put("keyPoints", keyPoints);
            m.put("fullScore", detail == null ? null : detail.getQuesScore());
            m.put("candidateAnswer", detail == null ? null : detail.getUserAnswer());
            m.put("marked", t.getMarkStatus() != null && t.getMarkStatus() != 0);
            m.put("myScore", t.getScore());
            m.put("myComment", t.getComment());
            items.add(m);
        }
        return PageResult.of(page.getTotal(), items, pageNum, pageSize);
    }

    // ---------------------------------------------------------------- MARK-04 提交评分（批量，upsert 允许改分）

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitScores(MarkScoresReq req) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (req.getTaskId() == null || req.getScores() == null || req.getScores().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "taskId 与 scores 不能为空");
        }
        MarkTask task = getById(req.getTaskId());
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "阅卷任务不存在: " + req.getTaskId());
        }
        // 阅卷教师须任务在本人名下；管理员不受归属限制（与 MARK-06 复核口径一致）
        if (!isAdmin(user) && (task.getMarkerId() == null || !task.getMarkerId().equals(user.getUserId()))) {
            throw new BizException(ErrorCode.MARK_TASK_INVALID, "任务不在本人任务内");
        }
        int saved = 0;
        Long sheetId = task.getSheetId();
        for (MarkScoresReq.ScoreItem item : req.getScores()) {
            if (item.getDetailId() == null || item.getScore() == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "评分项须含 detailId 与 score");
            }
            AnswerDetail detail = answerDetailMapper.selectById(item.getDetailId());
            if (detail == null || !detail.getSheetId().equals(sheetId)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "评分明细不属于该任务: " + item.getDetailId());
            }
            if (item.getScore().compareTo(BigDecimal.ZERO) < 0
                    || item.getScore().compareTo(detail.getQuesScore()) > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "分值越界 [0," + detail.getQuesScore() + "]: " + item.getScore());
            }
            applyScore(detail, item.getScore());
            answerDetailMapper.updateById(detail);

            MarkTask row = getOne(new QueryWrapper<MarkTask>()
                    .eq("detail_id", item.getDetailId()).eq("round_no", 1), false);
            if (row != null) {
                row.setMarkStatus(1);
                row.setScore(item.getScore());
                row.setComment(item.getComment());
                row.setMarkTime(LocalDateTime.now());
                updateById(row);
            }
            saved++;
        }
        gradingService.trySynthesize(sheetId);
        MarkTask fresh = getById(req.getTaskId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("savedCount", saved);
        m.put("taskStatus", fresh.getMarkStatus() != null && fresh.getMarkStatus() != 0 ? "DONE" : "DOING");
        return m;
    }

    /** 人工评分回写：满分=1 对，0 分=0 错，中间=2 半对；mark_status=2 人工已判 */
    static void applyScore(AnswerDetail detail, BigDecimal score) {
        detail.setScore(score);
        if (score.compareTo(detail.getQuesScore()) == 0) {
            detail.setIsRight(1);
        } else if (score.compareTo(BigDecimal.ZERO) == 0) {
            detail.setIsRight(0);
        } else {
            detail.setIsRight(2);
        }
        detail.setMarkStatus(2);
    }

    // ---------------------------------------------------------------- MARK-05 人工复核队列（detail mark_status=3）

    public PageResult<Map<String, Object>> reviewQueue(Long examId, String reviewType, long pageNum, long pageSize) {
        LoginUser user = SecurityUtils.getLoginUser();
        QueryWrapper<AnswerDetail> qw = new QueryWrapper<>();
        qw.eq("mark_status", 3);
        qw.eq(examId != null, "session_id", examId);
        if (!isAdmin(user)) {
            // 阅卷教师仅见分派给本人任务对应的复核明细
            qw.inSql("id", "SELECT detail_id FROM t_mark_task WHERE marker_id = " + user.getUserId());
        } else {
            String prefix = SecurityUtils.dataScopePathPrefix();
            if (prefix != null) {
                qw.inSql("session_id", "SELECT id FROM t_exam_session WHERE deleted = 0 AND create_org_path LIKE '"
                        + prefix.replace("'", "''") + "%'");
            }
        }
        qw.orderByAsc("id");
        Page<AnswerDetail> page = answerDetailMapper.selectPage(new Page<>(pageNum, pageSize), qw);

        Map<Long, String> examNames = examNamesOf(page.getRecords().stream().map(AnswerDetail::getSessionId).toList());
        Map<Long, AnswerSheet> sheetMap = new HashMap<>();
        List<Long> sheetIds = page.getRecords().stream().map(AnswerDetail::getSheetId).toList();
        if (!sheetIds.isEmpty()) {
            sheetMap = answerSheetMapper.selectBatchIds(sheetIds).stream()
                    .collect(Collectors.toMap(AnswerSheet::getId, Function.identity()));
        }
        Map<Long, AnswerSheet> sheets = sheetMap;
        return PageResult.of(page).map(d -> {
            AnswerSheet sheet = sheets.get(d.getSheetId());
            Map<String, Object> q = sheet == null ? null
                    : GradingService.snapshotQuestions(sheet).get(d.getQuesId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reviewId", d.getId());
            m.put("reviewType", "FILL_SIMILAR");
            m.put("examId", d.getSessionId());
            m.put("examName", examNames.get(d.getSessionId()));
            m.put("sheetId", d.getSheetId());
            m.put("detailId", d.getId());
            m.put("quesType", QuesTypeEnum.toApi(d.getQuesType()));
            m.put("stemText", q == null ? null : q.get("stem"));
            m.put("candidateAnswer", d.getUserAnswer());
            m.put("standardAnswer", q == null ? null : q.get("answer"));
            m.put("autoScore", d.getScore());
            m.put("fullScore", d.getQuesScore());
            return m;
        });
    }

    // ---------------------------------------------------------------- MARK-06 提交复核结论（按 reviewId 幂等覆盖）

    @Transactional(rollbackFor = Exception.class)
    public void submitReview(ReviewSubmitReq req) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (req.getReviewId() == null || req.getFinalScore() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "reviewId 与 finalScore 不能为空");
        }
        AnswerDetail detail = answerDetailMapper.selectById(req.getReviewId());
        if (detail == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "复核明细不存在: " + req.getReviewId());
        }
        if (!isAdmin(user)) {
            MarkTask task = getOne(new QueryWrapper<MarkTask>()
                    .eq("detail_id", detail.getId()).eq("round_no", 1), false);
            if (task == null || task.getMarkerId() == null || !task.getMarkerId().equals(user.getUserId())) {
                throw new BizException(ErrorCode.MARK_TASK_INVALID, "复核任务不在本人任务内");
            }
        }
        if (req.getFinalScore().compareTo(BigDecimal.ZERO) < 0
                || req.getFinalScore().compareTo(detail.getQuesScore()) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "分值越界 [0," + detail.getQuesScore() + "]: " + req.getFinalScore());
        }
        applyScore(detail, req.getFinalScore());
        answerDetailMapper.updateById(detail);
        // 关联阅卷任务完结
        MarkTask task = getOne(new QueryWrapper<MarkTask>()
                .eq("detail_id", detail.getId()).eq("round_no", 1), false);
        if (task != null) {
            task.setMarkStatus(1);
            task.setScore(req.getFinalScore());
            task.setComment(req.getComment());
            task.setMarkTime(LocalDateTime.now());
            updateById(task);
        }
        sysLogService.log(1, "MARK", "提交复核结论",
                "明细 " + detail.getId() + " 复核得分 " + req.getFinalScore()
                        + (req.getComment() == null ? "" : "，意见：" + req.getComment()), 1);
        gradingService.trySynthesize(detail.getSheetId());
    }

    // ---------------------------------------------------------------- 内部工具

    private static boolean isAdmin(LoginUser user) {
        return user.hasRole("SUPER_ADMIN") || user.hasRole("HQ_ADMIN")
                || user.hasRole("L2_ADMIN") || user.hasRole("SUB_ADMIN");
    }

    private Map<Long, String> examNamesOf(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        return examSessionMapper.selectBatchIds(sessionIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(ExamSession::getId, ExamSession::getSessionName));
    }

    /** 场次id → 场次结束时间（阅卷任务列表「截止时间」列用） */
    private Map<Long, LocalDateTime> sessionEndTimesOf(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        return examSessionMapper.selectList(new QueryWrapper<ExamSession>()
                        .select("id", "end_time").in("id", sessionIds.stream().distinct().toList()))
                .stream()
                .collect(Collectors.toMap(ExamSession::getId, ExamSession::getEndTime));
    }
}
