package com.group.exam.exam.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.PaperCopyReq;
import com.group.exam.exam.dto.PaperGradeRulesReq;
import com.group.exam.exam.dto.PaperQuestionsReq;
import com.group.exam.exam.dto.PaperSaveReq;
import com.group.exam.exam.dto.PaperUpdateReq;
import com.group.exam.exam.service.PaperService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 试卷管理（PAPER-01~10）
 */
@RestController
@RequestMapping("/api/v1/admin/papers")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    /** PAPER-01 试卷分页查询 */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String paperType,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "20") long pageSize) {
        requireAdmin();
        return Result.ok(paperService.page(keyword, paperType, status,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** PAPER-02 创建试卷（固定卷/随机策略卷） */
    @PostMapping
    public Result<Map<String, Long>> create(@RequestBody PaperSaveReq req) {
        requireAdmin();
        return Result.ok(Map.of("paperId", paperService.create(req)));
    }

    /** PAPER-03 试卷详情（含题目/策略/分值/等级规则） */
    @GetMapping("/{paperId}")
    public Result<Map<String, Object>> detail(@PathVariable Long paperId) {
        requireAdmin();
        return Result.ok(paperService.detail(paperId));
    }

    /** PAPER-04 编辑试卷基础信息与分数设计 */
    @PutMapping("/{paperId}")
    public Result<Void> update(@PathVariable Long paperId, @RequestBody PaperUpdateReq req) {
        requireAdmin();
        paperService.update(paperId, req);
        return Result.ok();
    }

    /** PAPER-05 删除试卷（未被引用） */
    @DeleteMapping("/{paperId}")
    public Result<Void> delete(@PathVariable Long paperId) {
        requireAdmin();
        paperService.delete(paperId);
        return Result.ok();
    }

    /** PAPER-06 固定卷选题与逐题分值设置（全量替换） */
    @PutMapping("/{paperId}/questions")
    public Result<Void> setQuestions(@PathVariable Long paperId, @RequestBody PaperQuestionsReq req) {
        requireAdmin();
        paperService.setQuestions(paperId, req);
        return Result.ok();
    }

    /** PAPER-07 试卷预览（含答案解析；RANDOM 卷试抽样卷） */
    @GetMapping("/{paperId}/preview")
    public Result<Map<String, Object>> preview(@PathVariable Long paperId) {
        requireAdmin();
        return Result.ok(paperService.preview(paperId));
    }

    /** PAPER-08 复制试卷新版本 */
    @PostMapping("/{paperId}/copy")
    public Result<Map<String, Long>> copy(@PathVariable Long paperId, @RequestBody(required = false) PaperCopyReq req) {
        requireAdmin();
        return Result.ok(Map.of("paperId", paperService.copy(paperId, req == null ? new PaperCopyReq() : req)));
    }

    /** PAPER-09 封存试卷（幂等） */
    @PutMapping("/{paperId}/seal")
    public Result<Void> seal(@PathVariable Long paperId) {
        requireAdmin();
        paperService.seal(paperId);
        return Result.ok();
    }

    /** PAPER-10 配置成绩等级规则 */
    @PutMapping("/{paperId}/grade-rules")
    public Result<Void> saveGradeRules(@PathVariable Long paperId, @RequestBody PaperGradeRulesReq req) {
        requireAdmin();
        paperService.saveGradeRules(paperId, req.getGradeRules());
        return Result.ok();
    }

    static void requireAdmin() {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN");
    }
}
