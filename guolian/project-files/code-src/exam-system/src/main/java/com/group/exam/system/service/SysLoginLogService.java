package com.group.exam.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.system.entity.SysLoginLog;
import com.group.exam.system.mapper.SysLoginLogMapper;
import org.springframework.stereotype.Service;

/**
 * 登录日志服务
 */
@Service
public class SysLoginLogService extends ServiceImpl<SysLoginLogMapper, SysLoginLog> {

    /** 记录一次登录（成功/失败） */
    public void record(Long userId, String jobNo, String ip, String terminal, String userAgent,
                       boolean success, String failReason) {
        SysLoginLog log = new SysLoginLog();
        log.setUserId(userId);
        log.setJobNo(jobNo);
        log.setLoginIp(ip);
        log.setTerminal(terminal);
        log.setUserAgent(userAgent);
        log.setLoginResult(success ? 1 : 0);
        log.setFailReason(success ? null : failReason);
        save(log);
    }

    /** 登出回填 logout_time */
    public void markLogout(Long userId) {
        if (userId != null) {
            baseMapper.markLogout(userId);
        }
    }
}
