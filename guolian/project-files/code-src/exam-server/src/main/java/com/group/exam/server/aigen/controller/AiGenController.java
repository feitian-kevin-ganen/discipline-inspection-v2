package com.group.exam.server.aigen.controller;

import com.group.exam.common.core.Result;
import com.group.exam.server.aigen.dto.QuestionUpdateReq;
import com.group.exam.server.aigen.dto.TaskCreateReq;
import com.group.exam.server.aigen.entity.AiGenQuestion;
import com.group.exam.server.aigen.service.AiGenService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 辅助生题（管理端，挂在 /admin/ques/ai-gen 下，随题目管理菜单进入）。
 * 模型当前为 mock（MockAiGenerator），接入真实模型时替换生成实现即可。
 */
@RestController
@RequestMapping("/api/v1/admin/ques/ai-gen")
public class AiGenController {

    private final AiGenService aiGenService;

    public AiGenController(AiGenService aiGenService) {
        this.aiGenService = aiGenService;
    }

    /** 生题选择器：已发布学习资料（可按资料分类含下级过滤） */
    @GetMapping("/materials")
    public Result<List<Map<String, Object>>> materials(@RequestParam(required = false) Long categoryId) {
        return Result.ok(aiGenService.materials(categoryId));
    }

    /** 创建生成任务（每个资料一个任务，异步执行） */
    @PostMapping("/tasks")
    public Result<List<Long>> createTasks(@Validated @RequestBody TaskCreateReq req) {
        return Result.ok(aiGenService.createTasks(req));
    }

    /** 任务列表（倒序，含生成时长） */
    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> tasks() {
        return Result.ok(aiGenService.listTasks());
    }

    /** 任务下的生成题目 */
    @GetMapping("/tasks/{taskId}/questions")
    public Result<List<AiGenQuestion>> questions(@PathVariable Long taskId) {
        return Result.ok(aiGenService.listQuestions(taskId));
    }

    /** 删除任务及其生成题目（生成中的任务不可删除） */
    @DeleteMapping("/tasks/{taskId}")
    public Result<Void> deleteTask(@PathVariable Long taskId) {
        aiGenService.deleteTask(taskId);
        return Result.ok();
    }

    /** 采纳前编辑（仅待处理题目） */
    @PutMapping("/questions/{questionId}")
    public Result<Void> update(@PathVariable Long questionId, @RequestBody QuestionUpdateReq req) {
        aiGenService.updateQuestion(questionId, req);
        return Result.ok();
    }

    /** 采纳：进入资料对应题库（自动去重） */
    @PostMapping("/questions/{questionId}/adopt")
    public Result<Long> adopt(@PathVariable Long questionId) {
        return Result.ok(aiGenService.adopt(questionId));
    }

    /** 丢弃 */
    @PostMapping("/questions/{questionId}/discard")
    public Result<Void> discard(@PathVariable Long questionId) {
        aiGenService.discard(questionId);
        return Result.ok();
    }
}
