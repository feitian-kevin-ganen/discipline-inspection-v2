package com.group.exam.system.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.system.dto.UserCreateRequest;
import com.group.exam.system.dto.UserRolesRequest;
import com.group.exam.system.dto.UserStatusRequest;
import com.group.exam.system.dto.UserUpdateRequest;
import com.group.exam.system.service.OrgUserService;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * USER 人员管理（API 文档 4.3）
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final OrgUserService orgUserService;

    public UserAdminController(OrgUserService orgUserService) {
        this.orgUserService = orgUserService;
    }

    /** USER-01 人员分页查询 */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(required = false) Long orgId,
                                                        @RequestParam(required = false) Boolean includeSub,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String roleCode,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(orgUserService.pageUsers(orgId, includeSub, keyword, roleCode, status,
                pageNum, Math.min(pageSize, 200)));
    }

    /** USER-02 新增人员 */
    @PostMapping
    public Result<Map<String, Object>> create(@Validated @RequestBody UserCreateRequest request) {
        return Result.ok(orgUserService.createUser(request));
    }

    /** USER-03 人员详情 */
    @GetMapping("/{userId}")
    public Result<Map<String, Object>> detail(@PathVariable Long userId) {
        return Result.ok(orgUserService.userDetail(userId));
    }

    /** USER-04 编辑人员（含单位变更） */
    @PutMapping("/{userId}")
    public Result<Void> update(@PathVariable Long userId, @Validated @RequestBody UserUpdateRequest request) {
        orgUserService.updateUser(userId, request);
        return Result.ok();
    }

    /** USER-05 删除人员（逻辑删除） */
    @DeleteMapping("/{userId}")
    public Result<Void> delete(@PathVariable Long userId) {
        orgUserService.deleteUser(userId);
        return Result.ok();
    }

    /** USER-06 账号启用/锁定/解锁 */
    @PutMapping("/{userId}/status")
    public Result<Void> updateStatus(@PathVariable Long userId, @Validated @RequestBody UserStatusRequest request) {
        orgUserService.updateStatus(userId, request.getStatus());
        return Result.ok();
    }

    /** USER-07 Excel 批量导入人员 */
    @PostMapping("/import")
    public Result<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false, defaultValue = "INSERT_ONLY") String mode) {
        Map<String, Object> data = orgUserService.importUsers(file, mode);
        if (((Number) data.get("failCount")).intValue() > 0) {
            return Result.error(ErrorCode.IMPORT_VALIDATION_FAILED.getCode(),
                    ErrorCode.IMPORT_VALIDATION_FAILED.getMessage(), data);
        }
        return Result.ok(data);
    }

    /** USER-08 下载导入模板 */
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + OrgUserService.importTemplateFilename());
        orgUserService.writeImportTemplate(response.getOutputStream());
        response.flushBuffer();
    }

    /** USER-09 分配角色 */
    @PutMapping("/{userId}/roles")
    public Result<Void> assignRoles(@PathVariable Long userId, @Validated @RequestBody UserRolesRequest request) {
        orgUserService.assignRoles(userId, request.getRoleCodes());
        return Result.ok();
    }

    /** USER-10 重置密码（生成初始密码） */
    @PutMapping("/{userId}/password/reset")
    public Result<Map<String, Object>> resetPassword(@PathVariable Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("initialPassword", orgUserService.resetPassword(userId));
        return Result.ok(data);
    }
}
