package com.group.exam.exam.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.exam.dto.CandidateIdsReq;
import com.group.exam.exam.dto.CandidateScopeReq;
import com.group.exam.exam.dto.ExamExtendReq;
import com.group.exam.exam.dto.ExamNotifyReq;
import com.group.exam.exam.dto.ExamSaveReq;
import com.group.exam.exam.dto.ExamStatusReq;
import com.group.exam.exam.dto.ForceSubmitReq;
import com.group.exam.exam.dto.RetakeReq;
import com.group.exam.exam.service.CandidateService;
import com.group.exam.exam.service.ExamSessionService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import static com.group.exam.exam.controller.PaperController.requireAdmin;

/**
 * 测试场次与下发（EXAM-01~14）
 */
@RestController
@RequestMapping("/api/v1/admin/exams")
public class ExamAdminController {

    private final ExamSessionService examSessionService;
    private final CandidateService candidateService;

    public ExamAdminController(ExamSessionService examSessionService, CandidateService candidateService) {
        this.examSessionService = examSessionService;
        this.candidateService = candidateService;
    }

    /** EXAM-01 场次分页查询 */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String startDate,
                                                        @RequestParam(required = false) String endDate,
                                                        @RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "20") long pageSize) {
        requireAdmin();
        // 前端日期范围为 yyyy-MM-dd（日粒度），也兼容 yyyy-MM-dd HH:mm:ss / ISO 带T；
        // endDate 日粒度时按当天 23:59:59 处理，避免漏掉当天场次
        LocalDateTime start = parseDateTime(startDate, false);
        LocalDateTime end = parseDateTime(endDate, true);
        return Result.ok(examSessionService.page(keyword, status, start, end,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    private static LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String s = value.trim();
        try {
            if (s.length() <= 10) {
                LocalDate d = LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
            }
            return LocalDateTime.parse(s.replace('T', ' '),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式错误（支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）: " + value);
        }
    }

    /** EXAM-02 创建测试场次 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody ExamSaveReq req) {
        requireAdmin();
        return Result.ok(examSessionService.create(req));
    }

    /** EXAM-03 场次详情 */
    @GetMapping("/{examId}")
    public Result<Map<String, Object>> detail(@PathVariable Long examId) {
        requireAdmin();
        return Result.ok(examSessionService.detail(examId));
    }

    /** EXAM-04 编辑场次（未开始） */
    @PutMapping("/{examId}")
    public Result<Void> update(@PathVariable Long examId, @RequestBody ExamSaveReq req) {
        requireAdmin();
        examSessionService.update(examId, req);
        return Result.ok();
    }

    /** EXAM-05 删除场次（未开始且未下发名单） */
    @DeleteMapping("/{examId}")
    public Result<Void> delete(@PathVariable Long examId) {
        requireAdmin();
        examSessionService.delete(examId);
        return Result.ok();
    }

    /** EXAM-06 场次状态流转（START/END/CANCEL） */
    @PutMapping("/{examId}/status")
    public Result<Void> transit(@PathVariable Long examId, @RequestBody ExamStatusReq req) {
        requireAdmin();
        examSessionService.transit(examId, req.getAction());
        return Result.ok();
    }

    /** EXAM-07 按组织范围生成考生名单快照（全量重建，幂等） */
    @PostMapping("/{examId}/candidates/scope")
    public Result<Map<String, Object>> buildByScope(@PathVariable Long examId, @RequestBody CandidateScopeReq req) {
        requireAdmin();
        return Result.ok(candidateService.buildByScope(examId, req));
    }

    /** EXAM-08 考生名单分页查询 */
    @GetMapping("/{examId}/candidates")
    public Result<PageResult<Map<String, Object>>> candidates(@PathVariable Long examId,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String sheetStatus,
                                                              @RequestParam(defaultValue = "1") long pageNum,
                                                              @RequestParam(defaultValue = "20") long pageSize) {
        requireAdmin();
        return Result.ok(candidateService.page(examId, keyword, sheetStatus,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** EXAM-09 追加考生 */
    @PostMapping("/{examId}/candidates")
    public Result<Map<String, Object>> addCandidates(@PathVariable Long examId, @RequestBody CandidateIdsReq req) {
        requireAdmin();
        return Result.ok(candidateService.add(examId, req.getUserIds()));
    }

    /** EXAM-10 剔除考生 */
    @DeleteMapping("/{examId}/candidates")
    public Result<Map<String, Object>> removeCandidates(@PathVariable Long examId, @RequestBody CandidateIdsReq req) {
        requireAdmin();
        return Result.ok(candidateService.remove(examId, req.getUserIds()));
    }

    /** EXAM-11 推送测试通知 */
    @PostMapping("/{examId}/notify")
    public Result<Map<String, Object>> notify(@PathVariable Long examId, @RequestBody(required = false) ExamNotifyReq req) {
        requireAdmin();
        return Result.ok(candidateService.notify(examId,
                req == null ? null : req.getMessage(), req == null ? null : req.getChannels(),
                req == null ? null : req.getUserIds()));
    }

    /** EXAM-12 延长测试时间 */
    @PutMapping("/{examId}/extend")
    public Result<Map<String, Object>> extend(@PathVariable Long examId, @RequestBody ExamExtendReq req) {
        requireAdmin();
        return Result.ok(examSessionService.extend(examId, req.getExtendMin()));
    }

    /** EXAM-13 强制收卷 */
    @PostMapping("/{examId}/force-submit")
    public Result<Map<String, Object>> forceSubmit(@PathVariable Long examId,
                                                   @RequestBody(required = false) ForceSubmitReq req) {
        requireAdmin();
        return Result.ok(examSessionService.forceSubmit(examId,
                req == null ? null : req.getUserIds(), req == null ? null : req.getReason()));
    }

    /** EXAM-14 补考设置 */
    @PutMapping("/{examId}/retake")
    public Result<Map<String, Object>> retake(@PathVariable Long examId, @RequestBody RetakeReq req) {
        requireAdmin();
        return Result.ok(examSessionService.setupRetake(examId, req));
    }
}
