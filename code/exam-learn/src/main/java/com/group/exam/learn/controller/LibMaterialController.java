package com.group.exam.learn.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.learn.dto.MaterialAuditRequest;
import com.group.exam.learn.dto.MaterialPublishRequest;
import com.group.exam.learn.dto.MaterialScopeRequest;
import com.group.exam.learn.service.LearnMaterialService;
import com.group.exam.learn.service.UrlContentService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * LIB 学习资料管理（API 文档 4.4，LIB-05~12）
 */
@RestController
@RequestMapping("/api/v1/admin/lib/materials")
public class LibMaterialController {

    private final LearnMaterialService materialService;
    private final UrlContentService urlContentService;

    public LibMaterialController(LearnMaterialService materialService, UrlContentService urlContentService) {
        this.materialService = materialService;
        this.urlContentService = urlContentService;
    }

    /** 外链解析：抓取外网文章提取正文 HTML，用于图文资料快速录入（内容入库后链接失效不影响展示） */
    @GetMapping("/fetch-url")
    public Result<Map<String, Object>> fetchUrl(@RequestParam String url) {
        return Result.ok(urlContentService.fetch(url));
    }

    /** LIB-05 资料分页查询（管理端） */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(required = false) Long catId,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String materialType,
                                                        @RequestParam(required = false) String sortBy,
                                                        @RequestParam(required = false) String sortOrder,
                                                        @RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(materialService.pageAdmin(catId, keyword, status, materialType,
                sortBy, sortOrder, pageNum, pageSize));
    }

    /** LIB-06 上传/新建资料（multipart） */
    @PostMapping
    public Result<Map<String, Object>> upload(@RequestParam(required = false) MultipartFile file,
                                              @RequestParam String title,
                                              @RequestParam Long catId,
                                              @RequestParam String materialType,
                                              @RequestParam(required = false) String summary,
                                              @RequestParam(required = false) String tags,
                                              @RequestParam(required = false) MultipartFile cover,
                                              @RequestParam(required = false) String contentHtml,
                                              @RequestParam(required = false) Integer durationSec,
                                              @RequestParam String scopeType,
                                              @RequestParam(required = false) String scopeOrgIds,
                                              @RequestParam(required = false) Integer sortNo) {
        return Result.ok(materialService.upload(file, title, catId, materialType, summary, tags, cover,
                contentHtml, durationSec, scopeType, scopeOrgIds, sortNo));
    }

    /** LIB-07 资料详情（管理端） */
    @GetMapping("/{materialId}")
    public Result<Map<String, Object>> detail(@PathVariable Long materialId) {
        return Result.ok(materialService.detail(materialId));
    }

    /** LIB-08 编辑资料元信息/换版（multipart） */
    @PutMapping("/{materialId}")
    public Result<Void> update(@PathVariable Long materialId,
                               @RequestParam(required = false) MultipartFile file,
                               @RequestParam(required = false) String title,
                               @RequestParam(required = false) Long catId,
                               @RequestParam(required = false) String materialType,
                               @RequestParam(required = false) String summary,
                               @RequestParam(required = false) String tags,
                               @RequestParam(required = false) MultipartFile cover,
                               @RequestParam(required = false) String contentHtml,
                               @RequestParam(required = false) Integer durationSec,
                               @RequestParam(required = false) String scopeType,
                               @RequestParam(required = false) String scopeOrgIds,
                               @RequestParam(required = false) Integer sortNo) {
        materialService.updateMaterial(materialId, file, title, catId, materialType, summary, tags, cover,
                contentHtml, durationSec, scopeType, scopeOrgIds, sortNo);
        return Result.ok();
    }

    /** LIB-09 删除资料 */
    @DeleteMapping("/{materialId}")
    public Result<Void> delete(@PathVariable Long materialId) {
        materialService.deleteMaterial(materialId);
        return Result.ok();
    }

    /** LIB-10 资料审核（通过/驳回，上级管理员或超管/总管） */
    @PostMapping("/{materialId}/audit")
    public Result<Void> audit(@PathVariable Long materialId,
                              @Validated @RequestBody MaterialAuditRequest request) {
        materialService.audit(materialId, request.getAction(), request.getComment());
        return Result.ok();
    }

    /** LIB-11 发布/下架 */
    @PutMapping("/{materialId}/publish")
    public Result<Void> publish(@PathVariable Long materialId,
                                @Validated @RequestBody MaterialPublishRequest request) {
        materialService.publish(materialId, request.getAction());
        return Result.ok();
    }

    /** LIB-12 设置可见范围 */
    @PutMapping("/{materialId}/scope")
    public Result<Void> updateScope(@PathVariable Long materialId,
                                    @Validated @RequestBody MaterialScopeRequest request) {
        materialService.updateScope(materialId, request);
        return Result.ok();
    }
}
