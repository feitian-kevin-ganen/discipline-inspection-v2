package com.group.exam.exam.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.service.WechatNotifyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 微信推送记录查询（管理端 NOTIFY-WX-04，V1.1 新增）
 */
@RestController
@RequestMapping("/api/v1/admin/notify-wx")
public class NotifyWxAdminController {

    private final WechatNotifyService wechatNotifyService;

    public NotifyWxAdminController(WechatNotifyService wechatNotifyService) {
        this.wechatNotifyService = wechatNotifyService;
    }

    /** NOTIFY-WX-04 推送记录分页查询（数据源自 t_notify_wx_log，按数据权限过滤） */
    @GetMapping("/records")
    public Result<PageResult<Map<String, Object>>> records(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN");
        return Result.ok(wechatNotifyService.pageRecords(examId, scene, status, keyword, startTime, endTime,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }
}
