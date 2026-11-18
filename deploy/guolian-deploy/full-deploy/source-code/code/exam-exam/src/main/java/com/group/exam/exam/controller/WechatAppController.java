package com.group.exam.exam.controller;

import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.SubscribeReportReq;
import com.group.exam.exam.dto.WechatBindReq;
import com.group.exam.exam.service.WechatNotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信订阅通知（考生端 NOTIFY-WX-01~03，V1.1 新增）
 */
@RestController
@RequestMapping("/api/v1/app/wechat")
public class WechatAppController {

    private final WechatNotifyService wechatNotifyService;

    public WechatAppController(WechatNotifyService wechatNotifyService) {
        this.wechatNotifyService = wechatNotifyService;
    }

    /** NOTIFY-WX-01 微信绑定（code 绑定；开发模式直接接受 openid；unbind=true 解绑） */
    @PostMapping("/bind")
    public Result<Map<String, Object>> bind(@RequestBody(required = false) WechatBindReq req) {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(wechatNotifyService.bind(req));
    }

    /** NOTIFY-WX-02 查询本人各场景订阅授权状态 */
    @GetMapping("/subscribe")
    public Result<Map<String, Object>> subscribeStatus() {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(wechatNotifyService.subscribeStatus());
    }

    /** NOTIFY-WX-03 上报订阅授权结果（wx.requestSubscribeMessage 回调，幂等） */
    @PutMapping("/subscribe")
    public Result<Map<String, Object>> reportSubscribe(@RequestBody SubscribeReportReq req) {
        SecurityUtils.requireAnyRole("EXAMINEE");
        return Result.ok(wechatNotifyService.reportSubscribe(req));
    }
}
