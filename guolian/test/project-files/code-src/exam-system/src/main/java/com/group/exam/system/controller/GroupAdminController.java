package com.group.exam.system.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.dto.GroupMembersRequest;
import com.group.exam.system.service.UserGroupService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GROUP 用户分组（管理端 GROUP-01~04，V1.1 新增）
 *
 * <p>分组为 t_org_user.user_group 标签字段口径，固定枚举 1派驻组/2子企业纪委/3纪检委员/9其他。
 */
@RestController
@RequestMapping("/api/v1/admin/groups")
public class GroupAdminController {

    private final UserGroupService userGroupService;

    public GroupAdminController(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    /** GROUP-01 分组清单（固定枚举 + 各分组人数，按数据权限过滤） */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        requireAdmin();
        return Result.ok(userGroupService.listGroups());
    }

    /** GROUP-02 分组成员分页查询 */
    @GetMapping("/{userGroup}/members")
    public Result<PageResult<Map<String, Object>>> members(@PathVariable Integer userGroup,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        requireAdmin();
        return Result.ok(userGroupService.pageMembers(userGroup, keyword,
                Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 200)));
    }

    /** GROUP-03 批量设置分组标签 */
    @PutMapping("/{userGroup}/members")
    public Result<Map<String, Object>> assign(@PathVariable Integer userGroup,
                                              @RequestBody GroupMembersRequest req) {
        requireAdmin();
        return Result.ok(userGroupService.assign(userGroup, req == null ? null : req.getUserIds()));
    }

    /** GROUP-04 批量移出分组（user_group 重置为 9 其他） */
    @DeleteMapping("/{userGroup}/members")
    public Result<Map<String, Object>> remove(@PathVariable Integer userGroup,
                                              @RequestBody GroupMembersRequest req) {
        requireAdmin();
        return Result.ok(userGroupService.remove(userGroup, req == null ? null : req.getUserIds()));
    }

    /** 管理员*：超管/总管/二级/子企 */
    private static void requireAdmin() {
        SecurityUtils.requireAnyRole("SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN");
    }
}
