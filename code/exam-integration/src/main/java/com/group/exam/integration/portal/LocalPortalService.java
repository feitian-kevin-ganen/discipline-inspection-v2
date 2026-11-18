package com.group.exam.integration.portal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.mapper.OrgUserMapper;
import com.group.exam.system.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本机模拟国联门户登录（profile=local）。
 * <p>
 * 只校验手机号是否已开通本地账号，密码不校验；登录成功后签发一次性 ticket（Redis，5 分钟），
 * 由前端跳转 /guolian-jijian/login?ticket=xxx 走与测试机一致的单点登录链路。
 * </p>
 */
@Slf4j
@Service
@Profile("local")
public class LocalPortalService {

    private static final String TICKET_KEY_PREFIX = "portal:ticket:";
    private static final Duration TICKET_TTL = Duration.ofMinutes(5);

    private final OrgUserMapper orgUserMapper;
    private final AuthService authService;
    private final StringRedisTemplate redisTemplate;

    public LocalPortalService(OrgUserMapper orgUserMapper, AuthService authService,
                              StringRedisTemplate redisTemplate) {
        this.orgUserMapper = orgUserMapper;
        this.authService = authService;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> login(String phone, String password) {
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "手机号不能为空");
        }
        OrgUser user = orgUserMapper.selectOne(new LambdaQueryWrapper<OrgUser>()
                .eq(OrgUser::getPhone, phone.trim())
                .last("limit 1"));
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "该手机号未开通本平台账号，请联系管理员");
        }
        if (user.getAccountStatus() != null && user.getAccountStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "账号状态异常，请联系管理员");
        }
        LoginUser loginUser = authService.buildLoginUser(user);
        String empId = StringUtils.hasText(user.getEmpId()) ? user.getEmpId() : user.getJobNo();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket, empId, TICKET_TTL);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", ticket);
        data.put("empId", empId);
        data.put("userId", user.getId());
        data.put("userName", user.getUserName());
        data.put("roles", loginUser.getRoles());
        data.put("isAdmin", loginUser.isAdminSide());
        log.info("模拟门户登录成功: phone={} empId={} roles={}", phone, empId, loginUser.getRoles());
        return data;
    }
}
