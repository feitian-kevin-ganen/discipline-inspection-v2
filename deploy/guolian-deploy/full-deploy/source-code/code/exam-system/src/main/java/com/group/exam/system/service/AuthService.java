package com.group.exam.system.service;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.dto.LoginRequest;
import com.group.exam.system.dto.PasswordChangeRequest;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.entity.SysRole;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务（登录/登出/刷新/改密/当前用户）
 */
@Service
public class AuthService {

    /** 登录限流 key 前缀：exam:ratelimit:login:{ip}:{jobNo}，1 分钟窗口 10 次 */
    private static final String RATE_LIMIT_PREFIX = "exam:ratelimit:login:";
    private static final int RATE_LIMIT_MAX = 10;
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60;

    private final OrgUserService orgUserService;
    private final OrgUnitService orgUnitService;
    private final SysRoleService sysRoleService;
    private final SysConfigService sysConfigService;
    private final SysLoginLogService sysLoginLogService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    public AuthService(OrgUserService orgUserService, OrgUnitService orgUnitService,
                       SysRoleService sysRoleService, SysConfigService sysConfigService,
                       SysLoginLogService sysLoginLogService, TokenService tokenService,
                       PasswordEncoder passwordEncoder, StringRedisTemplate redisTemplate) {
        this.orgUserService = orgUserService;
        this.orgUnitService = orgUnitService;
        this.sysRoleService = sysRoleService;
        this.sysConfigService = sysConfigService;
        this.sysLoginLogService = sysLoginLogService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    // ---------------------------------------------------------------- AUTH-01 登录

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(LoginRequest req, String ip, String userAgent) {
        String jobNo = req.getUsername().trim();
        checkRateLimit(ip, jobNo);

        OrgUser user = orgUserService.getByJobNo(jobNo);
        if (user == null) {
            failLog(null, jobNo, ip, req.getTerminal(), userAgent, "账号不存在");
            throw badCredentials();
        }
        if (user.getAccountStatus() != null && user.getAccountStatus() == 2) {
            failLog(user.getId(), jobNo, ip, req.getTerminal(), userAgent, "账号已锁定");
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getAccountStatus() != null && user.getAccountStatus() == 0) {
            // 停用账号与密码错误统一模糊提示，防账号枚举
            failLog(user.getId(), jobNo, ip, req.getTerminal(), userAgent, "账号已停用");
            throw badCredentials();
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            onPasswordFail(user, ip, req.getTerminal(), userAgent);
            throw badCredentials();
        }

        // 登录成功
        redisTemplate.delete(OrgUserService.LOGIN_FAIL_PREFIX + jobNo);
        LoginUser loginUser = buildLoginUser(user);
        Map<String, Object> tokens = tokenService.issueTokens(loginUser);

        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        orgUserService.updateById(user);
        sysLoginLogService.record(user.getId(), jobNo, ip, req.getTerminal(), userAgent, true, null);

        Map<String, Object> data = new LinkedHashMap<>(tokens);
        data.put("mustChangePassword", user.getAccountStatus() != null && user.getAccountStatus() == 3);
        data.put("user", userBrief(user, loginUser.getRoles()));
        return data;
    }

    private void checkRateLimit(String ip, String jobNo) {
        String key = RATE_LIMIT_PREFIX + ip + ":" + jobNo;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > RATE_LIMIT_MAX) {
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
    }

    private void onPasswordFail(OrgUser user, String ip, String terminal, String userAgent) {
        String key = OrgUserService.LOGIN_FAIL_PREFIX + user.getJobNo();
        Long fails = redisTemplate.opsForValue().increment(key);
        if (fails != null && fails == 1L) {
            redisTemplate.expire(key, 10, TimeUnit.MINUTES);
        }
        int lockThreshold = sysConfigService.getInt("security.loginFailLock", 5);
        if (fails != null && fails >= lockThreshold) {
            user.setAccountStatus(2);
            orgUserService.updateById(user);
            failLog(user.getId(), user.getJobNo(), ip, terminal, userAgent,
                    "连续失败 " + fails + " 次，账号已锁定");
            return;
        }
        failLog(user.getId(), user.getJobNo(), ip, terminal, userAgent, "密码错误");
    }

    private void failLog(Long userId, String jobNo, String ip, String terminal, String userAgent, String reason) {
        sysLoginLogService.record(userId, jobNo, ip, terminal, userAgent, false, reason);
    }

    private BizException badCredentials() {
        return new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
    }

    // ---------------------------------------------------------------- AUTH-02 登出

    public void logout(String accessToken, String refreshToken) {
        LoginUser current = SecurityUtils.getLoginUser();
        tokenService.blacklistAccessToken(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenService.revokeRefreshToken(refreshToken);
        }
        sysLoginLogService.markLogout(current.getUserId());
    }

    // ---------------------------------------------------------------- AUTH-03 刷新

    public Map<String, Object> refresh(String refreshToken) {
        LoginUser cached = tokenService.resolveRefreshToken(refreshToken);
        if (cached == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken 无效或已过期，请重新登录");
        }
        OrgUser user = orgUserService.getById(cached.getUserId());
        if (user == null || user.getAccountStatus() == null
                || user.getAccountStatus() == 0 || user.getAccountStatus() == 2) {
            tokenService.revokeRefreshToken(refreshToken);
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号状态不可用，请重新登录");
        }
        // 轮换：删旧发新，角色/数据权限按最新状态重建
        tokenService.revokeRefreshToken(refreshToken);
        return tokenService.issueTokens(buildLoginUser(user));
    }

    // ---------------------------------------------------------------- AUTH-04 修改密码

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeRequest req, String accessToken) {
        LoginUser current = SecurityUtils.getLoginUser();
        OrgUser user = orgUserService.getById(current.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_WRONG);
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setPwdUpdateTime(LocalDateTime.now());
        if (user.getAccountStatus() != null && user.getAccountStatus() == 3) {
            // 清除"首登强制改密"标记
            user.setAccountStatus(1);
        }
        orgUserService.updateById(user);
        // 吊销本人全部 token：黑名单当前 accessToken + 删除全部 refreshToken
        tokenService.blacklistAccessToken(accessToken);
        tokenService.revokeAllRefreshTokens(user.getId());
    }

    // ---------------------------------------------------------------- AUTH-05 当前用户

    public Map<String, Object> me() {
        LoginUser current = SecurityUtils.getLoginUser();
        OrgUser user = orgUserService.getById(current.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        List<SysRole> roles = sysRoleService.listRolesByUserId(user.getId());
        Set<String> permissions = new LinkedHashSet<>();
        for (SysRole role : roles) {
            if (role.getPermissions() != null) {
                permissions.addAll(role.getPermissions());
            }
        }
        OrgUnit unit = orgUnitService.getUnit(user.getOrgId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("name", user.getUserName());
        data.put("jobNo", user.getJobNo());
        data.put("orgId", user.getOrgId());
        data.put("orgName", unit == null ? null : unit.getUnitName());
        data.put("orgPath", user.getOrgPath());
        data.put("roles", roles.stream().map(SysRole::getRoleCode).toList());
        data.put("permissions", List.copyOf(permissions));
        data.put("menus", List.of());
        data.put("dataScopeOrgId", user.getOrgId());
        return data;
    }

    // ---------------------------------------------------------------- 内部工具

    /** 构建登录上下文：角色编码 + 数据权限（取所有角色最小值） */
    public LoginUser buildLoginUser(OrgUser user) {
        List<SysRole> roles = sysRoleService.listRolesByUserId(user.getId());
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setJobNo(user.getJobNo());
        loginUser.setUserName(user.getUserName());
        loginUser.setOrgId(user.getOrgId());
        loginUser.setOrgPath(user.getOrgPath());
        loginUser.setRoles(roles.stream().map(SysRole::getRoleCode).toList());
        loginUser.setDataScope(sysRoleService.dataScopeOf(roles));
        return loginUser;
    }

    private Map<String, Object> userBrief(OrgUser user, List<String> roleCodes) {
        OrgUnit unit = orgUnitService.getUnit(user.getOrgId());
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("userId", user.getId());
        brief.put("name", user.getUserName());
        brief.put("jobNo", user.getJobNo());
        brief.put("orgId", user.getOrgId());
        brief.put("orgName", unit == null ? null : unit.getUnitName());
        brief.put("roles", roleCodes);
        brief.put("avatarUrl", null);
        return brief;
    }
}
