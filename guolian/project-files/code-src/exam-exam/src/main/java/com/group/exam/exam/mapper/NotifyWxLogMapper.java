package com.group.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.exam.entity.NotifyWxLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微信推送记录 Mapper（t_notify_wx_log）
 */
@Mapper
public interface NotifyWxLogMapper extends BaseMapper<NotifyWxLog> {
}
