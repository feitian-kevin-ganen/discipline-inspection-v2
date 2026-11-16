package com.group.exam.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.system.entity.SysLoginLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {

    /** 登出时回填最近一次未登出记录的 logout_time */
    @Update("UPDATE t_sys_login_log SET logout_time = now() WHERE id = ("
            + "SELECT id FROM t_sys_login_log WHERE user_id = #{userId} AND logout_time IS NULL "
            + "ORDER BY login_time DESC LIMIT 1)")
    int markLogout(@Param("userId") Long userId);
}
