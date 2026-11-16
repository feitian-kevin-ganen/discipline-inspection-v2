package com.group.exam.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.system.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM t_sys_role r JOIN t_org_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /** 批量查询用户的角色编码：[{userId, roleCode}] */
    @Select({
            "<script>",
            "SELECT ur.user_id AS \"userId\", r.role_code AS \"roleCode\"",
            "FROM t_org_user_role ur JOIN t_sys_role r ON r.id = ur.role_id",
            "WHERE r.deleted = 0 AND ur.user_id IN",
            "<foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>",
            "</script>"
    })
    List<Map<String, Object>> selectRoleCodesByUserIds(@Param("userIds") Collection<Long> userIds);
}
