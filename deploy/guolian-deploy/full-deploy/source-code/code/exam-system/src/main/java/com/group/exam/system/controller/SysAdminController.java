package com.group.exam.system.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.dto.ConfigUpdateRequest;
import com.group.exam.system.dto.NoticePublishRequest;
import com.group.exam.system.service.SysAdminService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SYS 系统管理-管理端（API 文档 4.13，SYS-01 ~ SYS-07）
 */
@RestController
@RequestMapping("/api/v1/admin/sys")
public class SysAdminController {

    private final SysAdminService sysAdminService;

    public SysAdminController(SysAdminService sysAdminService) {
        this.sysAdminService = sysAdminService;
    }

    /** SYS-01 系统参数查询 */
    @GetMapping("/configs")
    public Result<List<Map<String, Object>>> listConfigs(@RequestParam(required = false) String group) {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN");
        return Result.ok(sysAdminService.listConfigs(group));
    }

    /** SYS-02 系统参数批量修改（仅超管） */
    @PutMapping("/configs")
    public Result<Void> updateConfigs(@Validated @RequestBody ConfigUpdateRequest request) {
        SecurityUtils.requireAnyRole("SUPER_ADMIN");
        sysAdminService.updateConfigs(request.getConfigs());
        return Result.ok();
    }

    /** SYS-03 登录日志查询 */
    @GetMapping("/logs/login")
    public Result<PageResult<Map<String, Object>>> loginLogs(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String result,
                                                             @RequestParam(required = false) String startTime,
                                                             @RequestParam(required = false) String endTime,
                                                             @RequestParam(defaultValue = "1") long pageNum,
                                                             @RequestParam(defaultValue = "20") long pageSize) {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN");
        return Result.ok(sysAdminService.pageLoginLogs(keyword, result, startTime, endTime,
                pageNum, Math.min(pageSize, 200)));
    }

    /** SYS-04 操作日志查询 */
    @GetMapping("/logs/operation")
    public Result<PageResult<Map<String, Object>>> operationLogs(@RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String module,
                                                                 @RequestParam(required = false) String startTime,
                                                                 @RequestParam(required = false) String endTime,
                                                                 @RequestParam(defaultValue = "1") long pageNum,
                                                                 @RequestParam(defaultValue = "20") long pageSize) {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN");
        return Result.ok(sysAdminService.pageOperationLogs(keyword, module, startTime, endTime,
                pageNum, Math.min(pageSize, 200)));
    }

    /** SYS-05 考试异常日志查询 */
    @GetMapping("/logs/exam-abnormal")
    public Result<PageResult<Map<String, Object>>> examAbnormalLogs(@RequestParam(required = false) Long examId,
                                                                    @RequestParam(required = false) String abnormalType,
                                                                    @RequestParam(defaultValue = "1") long pageNum,
                                                                    @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(sysAdminService.pageExamAbnormalLogs(examId, abnormalType,
                pageNum, Math.min(pageSize, 200)));
    }

    /** SYS-06 通知消息分页查询（管理端） */
    @GetMapping("/notices")
    public Result<PageResult<Map<String, Object>>> notices(@RequestParam(required = false) String noticeType,
                                                           @RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(sysAdminService.pageNotices(noticeType, pageNum, Math.min(pageSize, 200)));
    }

    /** SYS-07 发布通知消息 */
    @PostMapping("/notices")
    public Result<Map<String, Object>> publishNotice(@Validated @RequestBody NoticePublishRequest request) {
        return Result.ok(sysAdminService.publishNotice(request));
    }
}
