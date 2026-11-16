package com.group.exam.learn.controller;

import com.group.exam.common.core.Result;
import com.group.exam.learn.dto.CategoryCreateRequest;
import com.group.exam.learn.dto.CategoryUpdateRequest;
import com.group.exam.learn.service.LearnCategoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LIB 资料分类管理（API 文档 4.4，LIB-01~04）
 */
@RestController
@RequestMapping("/api/v1/admin/lib/categories")
public class LibCategoryController {

    private final LearnCategoryService categoryService;

    public LibCategoryController(LearnCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** LIB-01 资料分类树 */
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        return Result.ok(categoryService.tree());
    }

    /** LIB-02 新增分类 */
    @PostMapping
    public Result<Map<String, Object>> create(@Validated @RequestBody CategoryCreateRequest request) {
        Long catId = categoryService.create(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("catId", catId);
        return Result.ok(data);
    }

    /** LIB-03 编辑分类（含可见范围） */
    @PutMapping("/{catId}")
    public Result<Void> update(@PathVariable Long catId, @RequestBody CategoryUpdateRequest request) {
        categoryService.update(catId, request);
        return Result.ok();
    }

    /** LIB-04 删除分类（须为空） */
    @DeleteMapping("/{catId}")
    public Result<Void> delete(@PathVariable Long catId) {
        categoryService.delete(catId);
        return Result.ok();
    }
}
