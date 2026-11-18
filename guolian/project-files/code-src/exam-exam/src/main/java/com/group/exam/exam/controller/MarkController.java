package com.group.exam.exam.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.MarkScoresReq;
import com.group.exam.exam.dto.ReviewSubmitReq;
import com.group.exam.exam.service.MarkTaskService;
import com.group.exam.exam.service.ScorePublishService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 判分与阅卷（MARK-01~07）
 */
@RestController
@RequestMapping("/api/v1/admin/mark")
public class MarkController {

    private final MarkTaskService markTaskService;
    private final ScorePublishService scorePublishService;

    public MarkController(MarkTaskService markTaskService, ScorePublishService scorePublishService) {
        this.markTaskService = markTaskService;
        this.scorePublishService = scorePublishService;
    }

    /** MARK-01 阅卷任务列表 */
    @GetMapping("/tasks")
    public Result<PageResult<Map<String, Object>>> tasks(@RequestParam(required = false) Long examId,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "20") long pageSize) {
        requireMarkerOrAdmin();
        return Result.ok(markTaskService.pageTasks(examId, status,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** MARK-02 领取阅卷任务 */
    @PostMapping("/tasks/{taskId}/claim")
    public Result<Map<String, Object>> claim(@PathVariable Long taskId) {
        requireMarkerOrAdmin();
        return Result.ok(markTaskService.claim(taskId));
    }

    /** MARK-03 待阅答卷题目列表（匿名化） */
    @GetMapping("/tasks/{taskId}/items")
    public Result<PageResult<Map<String, Object>>> taskItems(@PathVariable Long taskId,
                                                             @RequestParam(required = false) Boolean marked,
                                                             @RequestParam(defaultValue = "1") long pageNum,
                                                             @RequestParam(defaultValue = "20") long pageSize) {
        requireMarkerOrAdmin();
        return Result.ok(markTaskService.taskItems(taskId, marked,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** MARK-04 提交评分（批量，含评语） */
    @PostMapping("/scores")
    public Result<Map<String, Object>> submitScores(@RequestBody MarkScoresReq req) {
        requireMarkerOrAdmin();
        return Result.ok(markTaskService.submitScores(req));
    }

    /** MARK-05 人工复核队列 */
    @GetMapping("/review-queue")
    public Result<PageResult<Map<String, Object>>> reviewQueue(@RequestParam(required = false) Long examId,
                                                               @RequestParam(required = false) String reviewType,
                                                               @RequestParam(defaultValue = "1") long pageNum,
                                                               @RequestParam(defaultValue = "20") long pageSize) {
        requireMarkerOrAdmin();
        return Result.ok(markTaskService.reviewQueue(examId, reviewType,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** MARK-06 提交复核结论 */
    @PostMapping("/review")
    public Result<Void> submitReview(@RequestBody ReviewSubmitReq req) {
        requireMarkerOrAdmin();
        markTaskService.submitReview(req);
        return Result.ok();
    }

    /** MARK-07 成绩合成并批量发布 */
    @PostMapping("/exams/{examId}/publish")
    public Result<Map<String, Object>> publish(@PathVariable Long examId,
                                               @RequestBody(required = false) Map<String, Object> body) {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN");
        return Result.ok(scorePublishService.publish(examId));
    }

    private static void requireMarkerOrAdmin() {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN", "MARKER");
    }
}
