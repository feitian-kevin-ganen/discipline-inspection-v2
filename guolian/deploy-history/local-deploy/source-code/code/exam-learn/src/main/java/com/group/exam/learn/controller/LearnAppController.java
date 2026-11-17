package com.group.exam.learn.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.learn.dto.HeartbeatRequest;
import com.group.exam.learn.service.HeartBeatService;
import com.group.exam.learn.service.LearnAppService;
import com.group.exam.learn.service.LearnTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LEARN 在线学习考生端（API 文档 4.5，LEARN-01~06）
 */
@RestController
@RequestMapping("/api/v1/app/learn")
public class LearnAppController {

    private final LearnAppService learnAppService;
    private final HeartBeatService heartBeatService;
    private final LearnTaskService learnTaskService;

    public LearnAppController(LearnAppService learnAppService, HeartBeatService heartBeatService,
                              LearnTaskService learnTaskService) {
        this.learnAppService = learnAppService;
        this.heartBeatService = heartBeatService;
        this.learnTaskService = learnTaskService;
    }

    /** LEARN-01 本人可见资料列表 */
    @GetMapping("/materials")
    public Result<PageResult<Map<String, Object>>> materials(@RequestParam(required = false) Long catId,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String sortBy,
                                                             @RequestParam(required = false) String sortOrder,
                                                             @RequestParam(defaultValue = "1") long pageNum,
                                                             @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(learnAppService.pageVisible(catId, keyword, sortBy, sortOrder, pageNum, pageSize));
    }

    /** LEARN-02 资料详情 + 本人学习断点 */
    @GetMapping("/materials/{materialId}")
    public Result<Map<String, Object>> detail(@PathVariable Long materialId) {
        return Result.ok(learnAppService.detail(materialId));
    }

    /** LEARN-03 获取资料文件地址 */
    @GetMapping("/materials/{materialId}/file")
    public Result<Map<String, Object>> file(@PathVariable Long materialId) {
        return Result.ok(learnAppService.fileAccess(materialId));
    }

    /** LEARN-03b 资料文件在线预览流（服务端代理转发，inline 输出，避免直链被下载工具拦截） */
    @GetMapping("/materials/{materialId}/file-content")
    public void fileContent(@PathVariable Long materialId,
                            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        learnAppService.streamFile(materialId, response);
    }

    /** LEARN-03d 视频流式播放（同源代理 + Range，mp4/webm 双格式；access_token 查询参数鉴权） */
    @GetMapping("/materials/{materialId}/video")
    public void video(@PathVariable Long materialId,
                      @RequestParam(required = false) String fmt,
                      jakarta.servlet.http.HttpServletRequest request,
                      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        learnAppService.streamVideo(materialId, fmt, request, response);
    }

    /** LEARN-03e 封面图（同源代理输出，access_token 查询参数鉴权） */
    @GetMapping("/materials/{materialId}/cover")
    public void cover(@PathVariable Long materialId,
                      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        learnAppService.streamCover(materialId, response);
    }

    /** LEARN-03c PDF 页图（逐页 JPEG 输出，前端只加载图片，彻底避开下载工具对 PDF 流的拦截） */
    @GetMapping("/materials/{materialId}/pages/{pageNo}")
    public void pageImage(@PathVariable Long materialId, @PathVariable int pageNo,
                          jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        learnAppService.streamPageImage(materialId, pageNo, response);
    }

    /** LEARN-04 学习进度心跳上报 */
    @PostMapping("/heartbeat")
    public Result<Map<String, Object>> heartbeat(@Validated @RequestBody HeartbeatRequest request) {
        return Result.ok(heartBeatService.heartbeat(request));
    }

    /** LEARN-05 获取断点续学进度 */
    @GetMapping("/records/{materialId}")
    public Result<Map<String, Object>> record(@PathVariable Long materialId) {
        return Result.ok(learnAppService.breakpoint(materialId));
    }

    /** LEARN-06 本人学习任务待办列表 */
    @GetMapping("/tasks")
    public Result<PageResult<Map<String, Object>>> tasks(@RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(learnTaskService.myTasks(status, pageNum, pageSize));
    }
}
