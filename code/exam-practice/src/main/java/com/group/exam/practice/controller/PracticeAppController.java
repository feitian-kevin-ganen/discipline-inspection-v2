package com.group.exam.practice.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.practice.dto.PracticeAnswerReq;
import com.group.exam.practice.dto.PracticeStartReq;
import com.group.exam.practice.service.PracticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 日常练习（考生端 PRAC-01~04，V1.1 新增）
 */
@RestController
@RequestMapping("/api/v1/app/practice")
public class PracticeAppController {

    private final PracticeService practiceService;

    public PracticeAppController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /** 一级法规分类列表（练习发起页分类选择），每项附启用题目数 */
    @GetMapping("/banks")
    public Result<List<Map<String, Object>>> banks() {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(practiceService.banks());
    }

    /** PRAC-01 发起练习（无状态抽题，不落库） */
    @PostMapping("/start")
    public Result<Map<String, Object>> start(@RequestBody(required = false) PracticeStartReq req) {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(practiceService.start(req));
    }

    /** PRAC-02 提交单题作答（即时反馈正误 + 解析，按 clientEventId 幂等） */
    @PostMapping("/answer")
    public Result<Map<String, Object>> answer(@RequestBody PracticeAnswerReq req) {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(practiceService.answer(req));
    }

    /** PRAC-03 本人练习记录分页（onlyWrong=true 错题回顾含解析） */
    @GetMapping("/records")
    public Result<PageResult<Map<String, Object>>> records(@RequestParam(required = false) String sourceLaw,
                                                           @RequestParam(required = false) Boolean onlyWrong,
                                                           @RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(practiceService.records(sourceLaw, onlyWrong,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** PRAC-04 本人练习统计（总量/分类正确率/趋势/薄弱知识点） */
    @GetMapping("/stat")
    public Result<Map<String, Object>> stat(@RequestParam(required = false) String periodType,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate) {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(practiceService.stat(periodType, startDate, endDate));
    }
}
