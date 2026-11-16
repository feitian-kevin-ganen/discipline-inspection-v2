package com.group.exam.stat.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.stat.service.StatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 考试统计（考生端 STAT-08）
 */
@RestController
@RequestMapping("/api/v1/app/stat")
public class StatAppController {

    private final StatService statService;

    public StatAppController(StatService statService) {
        this.statService = statService;
    }

    /** STAT-08 个人历次成绩单（仅已发布成绩的场次） */
    @GetMapping("/my-transcripts")
    public Result<PageResult<Map<String, Object>>> myTranscripts(@RequestParam(defaultValue = "1") long pageNum,
                                                                 @RequestParam(defaultValue = "20") long pageSize) {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(statService.myTranscripts(
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }
}
