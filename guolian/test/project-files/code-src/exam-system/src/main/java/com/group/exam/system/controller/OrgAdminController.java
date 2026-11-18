package com.group.exam.system.controller;

import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.dto.OrgCreateRequest;
import com.group.exam.system.dto.OrgParentRequest;
import com.group.exam.system.dto.OrgSortRequest;
import com.group.exam.system.dto.OrgStatusRequest;
import com.group.exam.system.dto.OrgUpdateRequest;
import com.group.exam.system.service.OrgUnitService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ORG 组织管理（API 文档 4.2）
 */
@RestController
@RequestMapping("/api/v1/admin/org")
public class OrgAdminController {

    private final OrgUnitService orgUnitService;

    public OrgAdminController(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    /** ORG-01 组织树查询 */
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree(@RequestParam(required = false) Long rootOrgId,
                                                  @RequestParam(required = false) Integer depth,
                                                  @RequestParam(required = false) Boolean includeDisabled) {
        return Result.ok(orgUnitService.tree(rootOrgId, depth, includeDisabled));
    }

    /** ORG-02 新增组织单位 */
    @PostMapping
    public Result<Map<String, Object>> create(@Validated @RequestBody OrgCreateRequest request) {
        Long orgId = orgUnitService.create(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orgId", orgId);
        return Result.ok(data);
    }

    /** ORG-03 编辑组织单位 */
    @PutMapping("/{orgId}")
    public Result<Void> update(@PathVariable Long orgId, @Validated @RequestBody OrgUpdateRequest request) {
        orgUnitService.update(orgId, request);
        return Result.ok();
    }

    /** ORG-04 删除组织单位 */
    @DeleteMapping("/{orgId}")
    public Result<Void> delete(@PathVariable Long orgId) {
        orgUnitService.delete(orgId);
        return Result.ok();
    }

    /** ORG-05 启用/停用组织单位 */
    @PutMapping("/{orgId}/status")
    public Result<Void> updateStatus(@PathVariable Long orgId, @Validated @RequestBody OrgStatusRequest request) {
        orgUnitService.updateStatus(orgId, request.getStatus());
        return Result.ok();
    }

    /** ORG-06 同级单位排序 */
    @PutMapping("/sort")
    public Result<Void> sort(@Validated @RequestBody OrgSortRequest request) {
        orgUnitService.sort(request);
        return Result.ok();
    }

    /** ORG-07 调整单位归属（仅总部管理员、超管） */
    @PutMapping("/{orgId}/parent")
    public Result<Void> moveParent(@PathVariable Long orgId, @Validated @RequestBody OrgParentRequest request) {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN");
        orgUnitService.moveParent(orgId, request.getNewParentId());
        return Result.ok();
    }
}
