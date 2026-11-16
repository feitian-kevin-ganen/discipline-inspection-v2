package com.group.exam.stat.controller;

import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.stat.service.StatService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 考试统计分析（管理端 STAT-01 ~ STAT-07）
 */
@RestController
@RequestMapping("/api/v1/admin/stat/exams")
public class StatAdminController {

    private final StatService statService;

    public StatAdminController(StatService statService) {
        this.statService = statService;
    }

    /** STAT-01 单场考试总览 */
    @GetMapping("/{examId}/overview")
    public Result<Map<String, Object>> overview(@PathVariable Long examId) {
        requireAdmin();
        return Result.ok(statService.overview(examId));
    }

    /** STAT-02 分层级单位汇总 */
    @GetMapping("/{examId}/org-summary")
    public Result<Map<String, Object>> orgSummary(@PathVariable Long examId,
                                                  @RequestParam(required = false) Long orgId) {
        requireAdmin();
        return Result.ok(statService.orgSummary(examId, orgId));
    }

    /** STAT-03 单位下钻（SUB_ORG 下级单位 / USER 人员明细分页） */
    @GetMapping("/{examId}/org-summary/{orgId}")
    public Result<Object> orgDrill(@PathVariable Long examId,
                                   @PathVariable Long orgId,
                                   @RequestParam(required = false) String detailType,
                                   @RequestParam(defaultValue = "1") long pageNum,
                                   @RequestParam(defaultValue = "20") long pageSize) {
        requireAdmin();
        return Result.ok(statService.orgDrill(examId, orgId, detailType,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** STAT-09 单位排名（按所属单位平均分排名，V1.2 新增） */
    @GetMapping("/{examId}/org-ranking")
    public Result<Map<String, Object>> orgRanking(@PathVariable Long examId,
                                                  @RequestParam(required = false) Long orgId) {
        requireAdmin();
        return Result.ok(statService.orgRanking(examId, orgId));
    }

    /** STAT-04 分数段分布 */
    @GetMapping("/{examId}/score-segments")
    public Result<List<Map<String, Object>>> scoreSegments(@PathVariable Long examId,
                                                           @RequestParam(required = false) Long orgId,
                                                           @RequestParam(required = false) String segments) {
        requireAdmin();
        return Result.ok(statService.scoreSegments(examId, orgId, segments));
    }

    /** STAT-05 优良中差等级分布 */
    @GetMapping("/{examId}/grade-distribution")
    public Result<List<Map<String, Object>>> gradeDistribution(@PathVariable Long examId,
                                                               @RequestParam(required = false) Long orgId) {
        requireAdmin();
        return Result.ok(statService.gradeDistribution(examId, orgId));
    }

    /** STAT-06 题目正确率与知识点分析 */
    @GetMapping("/{examId}/question-analysis")
    public Result<List<Map<String, Object>>> questionAnalysis(@PathVariable Long examId,
                                                              @RequestParam(required = false) String orderBy,
                                                              @RequestParam(required = false) Integer topN) {
        requireAdmin();
        Integer limit = topN == null ? 20 : Math.min(Math.max(topN, 1), 100);
        return Result.ok(statService.questionAnalysis(examId, orderBy, limit));
    }

    /** STAT-07 统计报表导出 Excel（同步写响应流） */
    @GetMapping("/{examId}/export")
    public void export(@PathVariable Long examId,
                       @RequestParam String exportType,
                       @RequestParam(required = false) Long orgId,
                       HttpServletResponse response) throws IOException {
        requireAdmin();
        String filename = URLEncoder.encode(
                "考试统计_" + examId + "_"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx",
                StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        statService.export(examId, exportType, orgId, response.getOutputStream());
    }

    /** 管理员*：超管/总管/二级/子企 */
    private static void requireAdmin() {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN");
    }
}
