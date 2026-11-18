package com.group.exam.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.entity.SysLog;
import com.group.exam.system.mapper.SysLogMapper;
import com.group.exam.system.util.IpUtils;
import org.springframework.stereotype.Service;

/**
 * 系统日志服务（操作/审计/考试异常日志写入，供其他模块调用）
 */
@Service
public class SysLogService extends ServiceImpl<SysLogMapper, SysLog> {

    /**
     * 写日志。自动带上当前登录人 userId/userName/orgPath 与请求 IP（未登录上下文时为空）。
     *
     * @param logType   1操作日志 2审计日志 3考试异常
     * @param module    模块：ORG/USER/LIB/QUES/PAPER/EXAM/MARK/SYS
     * @param operation 操作名（考试异常时存异常类型 FORCE_SUBMIT/SWITCH_EXCEED/KICK_OFFLINE）
     * @param content   明细内容
     * @param result    1成功 0失败
     */
    public void log(Integer logType, String module, String operation, String content, Integer result) {
        SysLog log = new SysLog();
        log.setLogType(logType);
        log.setModule(module);
        log.setOperation(operation);
        log.setContent(content);
        log.setResult(result == null ? 1 : result);
        try {
            LoginUser user = SecurityUtils.getLoginUser();
            log.setUserId(user.getUserId());
            log.setUserName(user.getUserName());
            log.setOrgPath(user.getOrgPath());
        } catch (Exception ignored) {
            // 未登录上下文（如登录失败日志），操作人留空
        }
        log.setIp(IpUtils.getClientIp());
        save(log);
    }
}
