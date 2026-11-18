package com.group.exam.exam.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.EnterExamReq;
import com.group.exam.exam.dto.SaveAnswersReq;
import com.group.exam.exam.dto.ScreenEventReq;
import com.group.exam.exam.dto.SubmitSheetReq;
import com.group.exam.exam.service.SheetService;
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
 * 在线测试考生端（TAKE-01~09）
 */
@RestController
@RequestMapping("/api/v1/app")
public class TakeAppController {

    private final SheetService sheetService;

    public TakeAppController(SheetService sheetService) {
        this.sheetService = sheetService;
    }

    /** TAKE-01 待考/历史测试列表 */
    @GetMapping("/exams")
    public Result<PageResult<Map<String, Object>>> myExams(@RequestParam(defaultValue = "TODO") String type,
                                                           @RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        requireExaminee();
        return Result.ok(sheetService.pageMyExams(SecurityUtils.getUserId(), type,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** TAKE-02 进入测试（须知确认） */
    @PostMapping("/exams/{examId}/enter")
    public Result<Map<String, Object>> enter(@PathVariable Long examId, @RequestBody(required = false) EnterExamReq req) {
        requireExaminee();
        return Result.ok(sheetService.enter(examId, req));
    }

    /** TAKE-03 服务端校时 */
    @GetMapping("/time")
    public Result<Map<String, Object>> time() {
        requireExaminee();
        return Result.ok(sheetService.serverTime());
    }

    /** TAKE-04 拉取试卷（脱敏，无答案解析） */
    @GetMapping("/exams/{examId}/paper")
    public Result<Map<String, Object>> paper(@PathVariable Long examId, @RequestParam Long sheetId) {
        requireExaminee();
        return Result.ok(sheetService.paper(examId, sheetId));
    }

    /** TAKE-05 批量/增量保存答案（心跳） */
    @PutMapping("/sheets/{sheetId}/answers")
    public Result<Map<String, Object>> saveAnswers(@PathVariable Long sheetId, @RequestBody SaveAnswersReq req) {
        requireExaminee();
        return Result.ok(sheetService.saveAnswers(sheetId, req));
    }

    /** TAKE-06 切屏/离开页面上报 */
    @PostMapping("/sheets/{sheetId}/screen-events")
    public Result<Map<String, Object>> screenEvent(@PathVariable Long sheetId, @RequestBody ScreenEventReq req) {
        requireExaminee();
        return Result.ok(sheetService.screenEvent(sheetId, req));
    }

    /** TAKE-07 交卷（幂等） */
    @PostMapping("/sheets/{sheetId}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long sheetId,
                                              @RequestBody(required = false) SubmitSheetReq req) {
        requireExaminee();
        return Result.ok(sheetService.submit(sheetId));
    }

    /** TAKE-08 答卷状态查询（断线续考恢复） */
    @GetMapping("/sheets/{sheetId}")
    public Result<Map<String, Object>> sheetState(@PathVariable Long sheetId) {
        requireExaminee();
        return Result.ok(sheetService.sheetState(sheetId));
    }

    /** TAKE-09 成绩查询（发布后） */
    @GetMapping("/exams/{examId}/result")
    public Result<Map<String, Object>> result(@PathVariable Long examId) {
        requireExaminee();
        return Result.ok(sheetService.result(examId));
    }

    private static void requireExaminee() {
        SecurityUtils.requireAnyRole("EXAMINEE");
    }
}
