package com.group.exam.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.dto.OrgCreateRequest;
import com.group.exam.system.dto.OrgSortRequest;
import com.group.exam.system.dto.OrgUpdateRequest;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.mapper.OrgUnitMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织单位服务（组织树/增删改/排序/归属调整，物化路径维护）
 */
@Service
public class OrgUnitService extends ServiceImpl<OrgUnitMapper, OrgUnit> {

    private final OrgUserService orgUserService;
    private final SysLogService sysLogService;

    public OrgUnitService(@Lazy OrgUserService orgUserService, SysLogService sysLogService) {
        this.orgUserService = orgUserService;
        this.sysLogService = sysLogService;
    }

    public OrgUnit getUnit(Long id) {
        return id == null ? null : getById(id);
    }

    /** 注意：listByIds(Collection<Long>) 直接继承自 ServiceImpl，无需重复声明 */
    public List<OrgUnit> listUnitsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return list(new QueryWrapper<OrgUnit>().in("id", ids));
    }

    public OrgUnit getByCode(String unitCode) {
        return getOne(new QueryWrapper<OrgUnit>().eq("unit_code", unitCode), false);
    }

    /** orgType 编码 → unit_type 数值 */
    public static int toUnitType(String orgType) {
        if (orgType == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单位类型不能为空");
        }
        return switch (orgType) {
            case "HQ" -> 1;
            case "L2" -> 2;
            case "SUB" -> 3;
            case "DEPT" -> 4;
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法单位类型: " + orgType);
        };
    }

    /** unit_type 数值 → orgType 编码 */
    public static String toOrgType(Integer unitType) {
        if (unitType == null) {
            return null;
        }
        return switch (unitType) {
            case 1 -> "HQ";
            case 2 -> "L2";
            case 3 -> "SUB";
            case 4 -> "DEPT";
            default -> String.valueOf(unitType);
        };
    }

    // ---------------------------------------------------------------- ORG-01 组织树

    public List<Map<String, Object>> tree(Long rootOrgId, Integer depth, Boolean includeDisabled) {
        LoginUser current = SecurityUtils.getLoginUser();
        Long rootId = null;
        Integer rootLevel = null;
        String prefix;
        if (rootOrgId != null) {
            OrgUnit root = getUnit(rootOrgId);
            if (root == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "组织单位不存在");
            }
            SecurityUtils.checkOrgScope(root.getPath());
            rootId = root.getId();
            rootLevel = root.getLevel();
            prefix = root.getPath();
        } else if (SecurityUtils.isSuperAdmin()) {
            prefix = null;
        } else {
            OrgUnit root = getUnit(current.getOrgId());
            if (root == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "组织单位不存在");
            }
            rootId = root.getId();
            rootLevel = root.getLevel();
            prefix = root.getPath();
        }

        QueryWrapper<OrgUnit> qw = new QueryWrapper<>();
        qw.likeRight(prefix != null, "path", prefix);
        if (!Boolean.TRUE.equals(includeDisabled)) {
            qw.eq("status", 1);
        }
        if (depth != null && depth > 0) {
            qw.le("level", rootLevel != null ? rootLevel + depth - 1 : depth);
        }
        qw.orderByAsc("level").orderByAsc("sort_no").orderByAsc("id");
        List<OrgUnit> units = list(qw);

        Map<Long, Long> userCounts = orgUserService.countGroupByOrg();
        Map<Long, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (OrgUnit unit : units) {
            nodeMap.put(unit.getId(), toNode(unit, userCounts.getOrDefault(unit.getId(), 0L)));
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (OrgUnit unit : units) {
            Map<String, Object> node = nodeMap.get(unit.getId());
            boolean isRoot = (rootId != null && unit.getId().equals(rootId))
                    || unit.getParentId() == null
                    || !nodeMap.containsKey(unit.getParentId());
            if (isRoot) {
                if (unit.getParentId() == null) {
                    node.put("parentId", 0);
                }
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children =
                        (List<Map<String, Object>>) nodeMap.get(unit.getParentId()).get("children");
                children.add(node);
            }
        }
        return roots;
    }

    private Map<String, Object> toNode(OrgUnit unit, long userCount) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("orgId", unit.getId());
        node.put("parentId", unit.getParentId());
        node.put("orgName", unit.getUnitName());
        node.put("orgCode", unit.getUnitCode());
        node.put("orgType", toOrgType(unit.getUnitType()));
        node.put("leaderName", unit.getLeaderName());
        node.put("phone", unit.getContactPhone());
        node.put("sortNo", unit.getSortNo());
        node.put("status", unit.getStatus());
        node.put("userCount", userCount);
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }

    // ---------------------------------------------------------------- ORG-02 新增

    @Transactional(rollbackFor = Exception.class)
    public Long create(OrgCreateRequest req) {
        // parentId 为空或 0 表示新增根级单位
        boolean root = req.getParentId() == null || req.getParentId() == 0L;
        OrgUnit parent = root ? null : getUnit(req.getParentId());
        if (!root) {
            if (parent == null) {
                throw new BizException(ErrorCode.PARENT_UNIT_NOT_EXISTS);
            }
            if (parent.getStatus() == null || parent.getStatus() != 1) {
                throw new BizException(ErrorCode.PARAM_ERROR, "上级单位已停用");
            }
            SecurityUtils.checkOrgScope(parent.getPath());
            if (parent.getLevel() + 1 > 10) {
                throw new BizException(ErrorCode.PARAM_ERROR, "组织层级超过上限");
            }
        }
        ensureCodeUnique(req.getOrgCode(), null);

        OrgUnit unit = new OrgUnit();
        unit.setParentId(root ? null : parent.getId());
        unit.setUnitName(req.getOrgName());
        unit.setUnitCode(req.getOrgCode());
        unit.setUnitType(toUnitType(req.getOrgType()));
        unit.setLeaderName(req.getLeaderName());
        unit.setContactPhone(req.getPhone());
        unit.setLevel(root ? 1 : parent.getLevel() + 1);
        unit.setStatus(1);
        unit.setPath("");
        unit.setSortNo(req.getSortNo() != null ? req.getSortNo() : nextSortNo(root ? null : parent.getId()));
        unit.setCreateBy(SecurityUtils.getUserId());
        save(unit);
        // 物化路径回填：父path + 自身id + "/"（根单位为 "/" + id + "/"）
        unit.setPath((root ? "/" : parent.getPath()) + unit.getId() + "/");
        updateById(unit);

        sysLogService.log(2, "ORG", "新增组织单位",
                "新增单位[" + unit.getUnitName() + "(" + unit.getUnitCode() + ")]，上级["
                        + (root ? "根级单位" : parent.getUnitName()) + "]", 1);
        return unit.getId();
    }

    private int nextSortNo(Long parentId) {
        QueryWrapper<OrgUnit> qw = new QueryWrapper<>();
        if (parentId == null) {
            qw.isNull("parent_id");
        } else {
            qw.eq("parent_id", parentId);
        }
        return (int) count(qw) + 1;
    }

    private void ensureCodeUnique(String orgCode, Long excludeId) {
        QueryWrapper<OrgUnit> qw = new QueryWrapper<OrgUnit>().eq("unit_code", orgCode);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        if (count(qw) > 0) {
            throw new BizException(ErrorCode.UNIT_CODE_EXISTS);
        }
    }

    // ---------------------------------------------------------------- ORG-03 编辑

    @Transactional(rollbackFor = Exception.class)
    public void update(Long orgId, OrgUpdateRequest req) {
        OrgUnit unit = requireInScope(orgId);
        if (req.getOrgCode() != null && !req.getOrgCode().equals(unit.getUnitCode())) {
            ensureCodeUnique(req.getOrgCode(), orgId);
            unit.setUnitCode(req.getOrgCode());
        }
        if (req.getOrgName() != null) {
            unit.setUnitName(req.getOrgName());
        }
        if (req.getOrgType() != null) {
            unit.setUnitType(toUnitType(req.getOrgType()));
        }
        if (req.getLeaderName() != null) {
            unit.setLeaderName(req.getLeaderName());
        }
        if (req.getPhone() != null) {
            unit.setContactPhone(req.getPhone());
        }
        if (req.getSortNo() != null) {
            unit.setSortNo(req.getSortNo());
        }
        unit.setUpdateBy(SecurityUtils.getUserId());
        updateById(unit);
        sysLogService.log(2, "ORG", "编辑组织单位", "编辑单位[" + unit.getUnitName() + "(id=" + orgId + ")]", 1);
    }

    // ---------------------------------------------------------------- ORG-04 删除

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long orgId) {
        OrgUnit unit = requireInScope(orgId);
        long children = count(new QueryWrapper<OrgUnit>().eq("parent_id", orgId));
        if (children > 0) {
            throw new BizException(ErrorCode.UNIT_HAS_CHILDREN);
        }
        long users = orgUserService.count(new QueryWrapper<OrgUser>().eq("org_id", orgId));
        if (users > 0) {
            throw new BizException(ErrorCode.UNIT_HAS_CHILDREN);
        }
        removeById(orgId);
        sysLogService.log(2, "ORG", "删除组织单位", "删除单位[" + unit.getUnitName() + "(id=" + orgId + ")]", 1);
    }

    // ---------------------------------------------------------------- ORG-05 启停

    public void updateStatus(Long orgId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "status 仅支持 1启用/0停用");
        }
        OrgUnit unit = requireInScope(orgId);
        unit.setStatus(status);
        unit.setUpdateBy(SecurityUtils.getUserId());
        updateById(unit);
        sysLogService.log(2, "ORG", status == 1 ? "启用组织单位" : "停用组织单位",
                "单位[" + unit.getUnitName() + "(id=" + orgId + ")]", 1);
    }

    // ---------------------------------------------------------------- ORG-06 同级排序

    @Transactional(rollbackFor = Exception.class)
    public void sort(OrgSortRequest req) {
        OrgUnit parent = getUnit(req.getParentId());
        if (parent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "上级单位不存在");
        }
        SecurityUtils.checkOrgScope(parent.getPath());
        int sort = 1;
        for (Long orgId : req.getOrderedOrgIds()) {
            update(new UpdateWrapper<OrgUnit>()
                    .eq("id", orgId)
                    .eq("parent_id", req.getParentId())
                    .set("sort_no", sort++));
        }
    }

    // ---------------------------------------------------------------- ORG-07 调整归属

    @Transactional(rollbackFor = Exception.class)
    public void moveParent(Long orgId, Long newParentId) {
        OrgUnit unit = getUnit(orgId);
        if (unit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织单位不存在");
        }
        OrgUnit newParent = getUnit(newParentId);
        if (newParent == null) {
            throw new BizException(ErrorCode.PARENT_UNIT_NOT_EXISTS);
        }
        SecurityUtils.checkOrgScope(unit.getPath());
        SecurityUtils.checkOrgScope(newParent.getPath());
        if (orgId.equals(newParentId) || newParent.getPath().startsWith(unit.getPath())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能迁移到自身或其下级单位");
        }
        if (newParent.getLevel() + 1 > 10) {
            throw new BizException(ErrorCode.PARAM_ERROR, "组织层级超过上限");
        }

        String oldPath = unit.getPath();
        String newPath = newParent.getPath() + unit.getId() + "/";
        int levelDelta = newParent.getLevel() + 1 - unit.getLevel();

        // 级联更新整棵子树 path/level
        List<OrgUnit> subtree = list(new QueryWrapper<OrgUnit>().likeRight("path", oldPath));
        for (OrgUnit node : subtree) {
            node.setPath(newPath + node.getPath().substring(oldPath.length()));
            node.setLevel(node.getLevel() + levelDelta);
            if (node.getId().equals(orgId)) {
                node.setParentId(newParentId);
            }
            node.setUpdateBy(SecurityUtils.getUserId());
            updateById(node);
        }
        // 同步更新受影响在册人员的 org_path
        List<OrgUser> users = orgUserService.list(new QueryWrapper<OrgUser>().likeRight("org_path", oldPath));
        for (OrgUser user : users) {
            user.setOrgPath(newPath + user.getOrgPath().substring(oldPath.length()));
            orgUserService.updateById(user);
        }
        sysLogService.log(2, "ORG", "调整单位归属",
                "单位[" + unit.getUnitName() + "(id=" + orgId + ")] 迁移至[" + newParent.getUnitName()
                        + "(id=" + newParentId + ")]，子树单位 " + subtree.size() + " 个，人员 " + users.size() + " 人", 1);
    }

    private OrgUnit requireInScope(Long orgId) {
        OrgUnit unit = getUnit(orgId);
        if (unit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织单位不存在");
        }
        SecurityUtils.checkOrgScope(unit.getPath());
        return unit;
    }

    /** orgId → 单位名称 */
    public Map<Long, String> mapUnitNames(Collection<Long> orgIds) {
        Map<Long, String> result = new HashMap<>();
        for (OrgUnit unit : listUnitsByIds(orgIds)) {
            result.put(unit.getId(), unit.getUnitName());
        }
        return result;
    }
}
