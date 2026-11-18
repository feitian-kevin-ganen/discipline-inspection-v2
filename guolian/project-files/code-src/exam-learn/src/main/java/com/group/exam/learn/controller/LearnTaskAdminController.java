package com.group.exam.learn.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.learn.dto.LearnTaskCreateRequest;
import com.group.exam.learn.service.LearnTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LEARN 学习任务管理端（API 文档 4.5，LEARN-07/08）
 */
@RestController
@RequestMapping("/api/v1/admin/learn/tasks")
public class LearnTaskAdminController {

    private final LearnTaskService learnTaskService;

    public LearnTaskAdminController(LearnTaskService learnTaskService) {
        this.learnTaskService = learnTaskService;
    }

    /** LEARN-07 学习任务分页查询（管理端） */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(learnTaskService.pageAdmin(keyword, status, pageNum, pageSize));
    }

    /** LEARN-08 下达学习任务 */
    @PostMapping
    public Result<Map<String, Object>> create(@Validated @RequestBody LearnTaskCreateRequest request) {
        return Result.ok(learnTaskService.create(request));
    }
}
