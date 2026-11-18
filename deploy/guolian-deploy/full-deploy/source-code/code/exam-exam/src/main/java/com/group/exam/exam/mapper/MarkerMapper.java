package com.group.exam.exam.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 阅卷教师查询（t_org_user_role join t_sys_role，role_code='MARKER'）
 */
public interface MarkerMapper {

    /** 全部正常状态的阅卷教师：[{id, userName}] */
    @Select("SELECT u.id AS id, u.user_name AS \"userName\" FROM t_org_user u "
            + "JOIN t_org_user_role ur ON ur.user_id = u.id "
            + "JOIN t_sys_role r ON r.id = ur.role_id AND r.deleted = 0 "
            + "WHERE r.role_code = 'MARKER' AND u.deleted = 0 AND u.account_status = 1 "
            + "ORDER BY u.id")
    List<Map<String, Object>> listMarkers();
}
