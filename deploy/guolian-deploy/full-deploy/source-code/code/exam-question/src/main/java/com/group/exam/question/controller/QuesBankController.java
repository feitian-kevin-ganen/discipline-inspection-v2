package com.group.exam.question.controller;

import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.question.dto.BankSaveReq;
import com.group.exam.question.dto.BankTreeVO;
import com.group.exam.question.service.QuesBankService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 题库分类（QUES-01~04）
 */
@RestController
@RequestMapping("/api/v1/admin/ques/banks")
public class QuesBankController {

    private final QuesBankService quesBankService;

    public QuesBankController(QuesBankService quesBankService) {
        this.quesBankService = quesBankService;
    }

    /** QUES-01 题库分类树 */
    @GetMapping("/tree")
    public Result<List<BankTreeVO>> tree() {
        requireAdmin();
        return Result.ok(quesBankService.tree());
    }

    /** QUES-02 新增题库分类 */
    @PostMapping
    public Result<Map<String, Long>> create(@Validated @RequestBody BankSaveReq req) {
        requireAdmin();
        return Result.ok(Map.of("bankId", quesBankService.create(req)));
    }

    /** QUES-03 编辑题库分类 */
    @PutMapping("/{bankId}")
    public Result<Void> update(@PathVariable Long bankId, @Validated @RequestBody BankSaveReq req) {
        requireAdmin();
        quesBankService.update(bankId, req);
        return Result.ok();
    }

    /** QUES-04 删除题库分类（须无子分类且无题目） */
    @DeleteMapping("/{bankId}")
    public Result<Void> delete(@PathVariable Long bankId) {
        requireAdmin();
        quesBankService.delete(bankId);
        return Result.ok();
    }

    /** 题库接口角色：管理员*（超管/总管/二级/子企） */
    static void requireAdmin() {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN");
    }
}
