package com.group.exam.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.entity.SysNotice;
import com.group.exam.system.service.SysNoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SYS 系统管理-考生端站内消息（API 文档 4.13，SYS-08 / SYS-09）
 */
@RestController
@RequestMapping("/api/v1/app/notices")
public class AppNoticeController {

    private final SysNoticeService sysNoticeService;

    public AppNoticeController(SysNoticeService sysNoticeService) {
        this.sysNoticeService = sysNoticeService;
    }

    /** SYS-08 本人站内消息列表（含未读数） */
    @GetMapping
    public Result<Map<String, Object>> myNotices(@RequestParam(required = false) Boolean read,
                                                 @RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "20") long pageSize) {
        Long userId = SecurityUtils.getUserId();
        QueryWrapper<SysNotice> qw = new QueryWrapper<>();
        qw.eq("receiver_id", userId);
        if (read != null) {
            qw.eq("read_status", read ? 1 : 0);
        }
        qw.orderByDesc("create_time");
        Page<SysNotice> page = sysNoticeService.page(new Page<>(pageNum, Math.min(pageSize, 200)), qw);

        long unreadCount = sysNoticeService.count(new QueryWrapper<SysNotice>()
                .eq("receiver_id", userId).eq("read_status", 0));

        PageResult<Map<String, Object>> pageResult = PageResult.of(page).map(this::toItem);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", pageResult.getTotal());
        data.put("list", pageResult.getList());
        data.put("pageNum", pageResult.getPageNum());
        data.put("pageSize", pageResult.getPageSize());
        data.put("unreadCount", unreadCount);
        return Result.ok(data);
    }

    /** SYS-09 标记消息已读（幂等；noticeId=0 表示全部已读） */
    @PutMapping("/{noticeId}/read")
    public Result<Void> markRead(@PathVariable Long noticeId) {
        Long userId = SecurityUtils.getUserId();
        if (noticeId == 0) {
            sysNoticeService.update(new UpdateWrapper<SysNotice>()
                    .eq("receiver_id", userId)
                    .eq("read_status", 0)
                    .set("read_status", 1)
                    .set("read_time", LocalDateTime.now()));
            return Result.ok();
        }
        SysNotice notice = sysNoticeService.getById(noticeId);
        if (notice == null || !userId.equals(notice.getReceiverId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (notice.getReadStatus() == null || notice.getReadStatus() == 0) {
            notice.setReadStatus(1);
            notice.setReadTime(LocalDateTime.now());
            sysNoticeService.updateById(notice);
        }
        return Result.ok();
    }

    private Map<String, Object> toItem(SysNotice notice) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("noticeId", notice.getId());
        item.put("noticeType", SysNoticeService.toNoticeTypeCode(notice.getNoticeType()));
        item.put("title", notice.getTitle());
        item.put("content", notice.getContent());
        item.put("read", notice.getReadStatus() != null && notice.getReadStatus() == 1);
        item.put("sendTime", notice.getCreateTime());
        Map<String, Object> bizLink = null;
        if (notice.getBizType() != null && notice.getBizId() != null) {
            bizLink = new LinkedHashMap<>();
            switch (notice.getBizType()) {
                case "session" -> bizLink.put("examId", notice.getBizId());
                case "task" -> bizLink.put("taskId", notice.getBizId());
                default -> bizLink.put("bizId", notice.getBizId());
            }
        }
        item.put("bizLink", bizLink);
        return item;
    }
}
