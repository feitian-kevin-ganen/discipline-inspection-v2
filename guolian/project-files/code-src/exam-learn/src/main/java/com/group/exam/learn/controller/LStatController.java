package com.group.exam.learn.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.learn.service.LearnStatService;
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
 * LSTAT 学习统计（API 文档 4.6，LSTAT-01~05）
 */
@RestController
@RequestMapping("/api/v1")
public class LStatController {

    private final LearnStatService learnStatService;

    public LStatController(LearnStatService learnStatService) {
        this.learnStatService = learnStatService;
    }

    /** LSTAT-01 个人学习统计 */
    @GetMapping("/app/lstat/personal")
    public Result<Map<String, Object>> personal(@RequestParam(required = false) String periodType,
                                                @RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate) {
        return Result.ok(learnStatService.personal(periodType, startDate, endDate));
    }

    /** LSTAT-02 单位学习汇总（按层级） */
    @GetMapping("/admin/lstat/org-summary")
    public Result<Map<String, Object>> orgSummary(@RequestParam(required = false) Long orgId,
                                                  @RequestParam(required = false) String periodType,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate) {
        return Result.ok(learnStatService.orgSummary(orgId, periodType, startDate, endDate));
    }

    /** LSTAT-03 单位下钻明细与未达标名单 */
    @GetMapping("/admin/lstat/org-summary/{orgId}")
    public Result<?> orgDetail(@PathVariable Long orgId,
                               @RequestParam(defaultValue = "SUB_ORG") String detailType,
                               @RequestParam(required = false) String periodType,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate,
                               @RequestParam(defaultValue = "1") long pageNum,
                               @RequestParam(defaultValue = "20") long pageSize) {
        if ("USER".equals(detailType)) {
            PageResult<Map<String, Object>> page =
                    learnStatService.orgUserDetail(orgId, periodType, startDate, endDate, pageNum, pageSize);
            return Result.ok(page);
        }
        return Result.ok(learnStatService.orgSummary(orgId, periodType, startDate, endDate));
    }

    /** LSTAT-04 学习排行（单位/个人） */
    @GetMapping("/admin/lstat/ranking")
    public Result<List<Map<String, Object>>> ranking(@RequestParam String rankType,
                                                     @RequestParam(required = false) Long orgId,
                                                     @RequestParam(required = false) String periodType,
                                                     @RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate,
                                                     @RequestParam(required = false) Integer topN) {
        return Result.ok(learnStatService.ranking(rankType, orgId, periodType, startDate, endDate, topN));
    }

    /** LSTAT-06 已完成资料名称清单（悬停提示，userId/orgId 二选一） */
    @GetMapping("/admin/lstat/completed-materials")
    public Result<List<String>> completedMaterials(@RequestParam(required = false) Long userId,
                                                   @RequestParam(required = false) Long orgId,
                                                   @RequestParam(required = false) String periodType,
                                                   @RequestParam(required = false) String startDate,
                                                   @RequestParam(required = false) String endDate) {
        return Result.ok(learnStatService.completedMaterialTitles(userId, orgId, periodType, startDate, endDate));
    }

    /** LSTAT-05 学习统计导出 Excel（同步写响应流） */
    @GetMapping("/admin/lstat/export")
    public void export(@RequestParam String exportType,
                       @RequestParam(required = false) Long orgId,
                       @RequestParam(required = false) String periodType,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       HttpServletResponse response) throws IOException {
        String filename = URLEncoder.encode(
                "学习统计_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx",
                StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        learnStatService.export(exportType, orgId, periodType, startDate, endDate, response.getOutputStream());
    }
}
