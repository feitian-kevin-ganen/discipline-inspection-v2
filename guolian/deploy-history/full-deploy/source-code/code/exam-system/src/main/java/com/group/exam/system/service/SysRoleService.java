package com.group.exam.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.system.entity.SysRole;
import com.group.exam.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色服务
 */
@Service
public class SysRoleService extends ServiceImpl<SysRoleMapper, SysRole> {

    public SysRole getByCode(String roleCode) {
        return getOne(new QueryWrapper<SysRole>().eq("role_code", roleCode), false);
    }

    public List<SysRole> listByCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        return list(new QueryWrapper<SysRole>().in("role_code", roleCodes));
    }

    /** 某人角色列表 */
    public List<SysRole> listRolesByUserId(Long userId) {
        return baseMapper.selectRolesByUserId(userId);
    }

    /** 某人角色编码列表 */
    public List<String> listRoleCodesByUserId(Long userId) {
        return listRolesByUserId(userId).stream().map(SysRole::getRoleCode).toList();
    }

    /** 批量：userId → 角色编码列表 */
    public Map<Long, List<String>> mapRoleCodesByUserIds(Collection<Long> userIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : baseMapper.selectRoleCodesByUserIds(userIds)) {
            Long userId = ((Number) row.get("userId")).longValue();
            String roleCode = (String) row.get("roleCode");
            result.computeIfAbsent(userId, k -> new java.util.ArrayList<>()).add(roleCode);
        }
        return result;
    }

    /** 某人数据权限范围（取所有角色的最小值，无角色按 3 仅本人） */
    public int dataScopeOf(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return 3;
        }
        return roles.stream().map(SysRole::getDataScope).filter(java.util.Objects::nonNull)
                .min(Integer::compareTo).orElse(3);
    }
}
