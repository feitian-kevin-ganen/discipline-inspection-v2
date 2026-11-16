package com.group.exam.system.service;

import com.group.exam.common.security.JwtAuthFilter;
import com.group.exam.common.security.JwtUtils;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.util.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Token 服务：accessToken 黑名单、refreshToken 签发/轮换/吊销（Redis）
 */
@Service
public class TokenService {

    /** refreshToken 存储 key 前缀：exam:refresh:{token} → LoginUser JSON */
    public static final String REFRESH_PREFIX = "exam:refresh:";
    /** 用户 → refreshToken 集合：exam:refresh:user:{userId}，用于按人吊销 */
    public static final String USER_REFRESH_PREFIX = "exam:refresh:user:";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtils jwtUtils;

    @Value("${exam.jwt.refresh-expire-seconds:604800}")
    private long refreshExpireSeconds;

    public TokenService(StringRedisTemplate redisTemplate, JwtUtils jwtUtils) {
        this.redisTemplate = redisTemplate;
        this.jwtUtils = jwtUtils;
    }

    /** 签发双 token，返回 {accessToken, refreshToken, expiresIn} */
    public Map<String, Object> issueTokens(LoginUser user) {
        String accessToken = jwtUtils.createAccessToken(user);
        String refreshToken = "rft_" + UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(REFRESH_PREFIX + refreshToken, JsonUtils.toJson(user),
                refreshExpireSeconds, TimeUnit.SECONDS);
        String userKey = USER_REFRESH_PREFIX + user.getUserId();
        redisTemplate.opsForSet().add(userKey, refreshToken);
        redisTemplate.expire(userKey, refreshExpireSeconds, TimeUnit.SECONDS);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", jwtUtils.getAccessExpireSeconds());
        return result;
    }

    /** 解析 refreshToken 对应的登录用户，无效/过期返回 null */
    public LoginUser resolveRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(REFRESH_PREFIX + refreshToken);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return JsonUtils.fromJson(json, LoginUser.class);
    }

    /** 轮换 refreshToken：删旧发新，旧 token 无效返回 null */
    public Map<String, Object> rotate(String oldRefreshToken) {
        LoginUser user = resolveRefreshToken(oldRefreshToken);
        if (user == null) {
            return null;
        }
        revokeRefreshToken(oldRefreshToken);
        return issueTokens(user);
    }

    /** 吊销单个 refreshToken */
    public void revokeRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        LoginUser user = resolveRefreshToken(refreshToken);
        redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        if (user != null && user.getUserId() != null) {
            redisTemplate.opsForSet().remove(USER_REFRESH_PREFIX + user.getUserId(), refreshToken);
        }
    }

    /** 吊销某人全部 refreshToken（改密/锁定/停用/删除时调用） */
    public void revokeAllRefreshTokens(Long userId) {
        if (userId == null) {
            return;
        }
        String userKey = USER_REFRESH_PREFIX + userId;
        Set<String> tokens = redisTemplate.opsForSet().members(userKey);
        if (tokens != null) {
            for (String token : tokens) {
                redisTemplate.delete(REFRESH_PREFIX + token);
            }
        }
        redisTemplate.delete(userKey);
    }

    /** 当前 accessToken 加入黑名单（登出/改密） */
    public void blacklistAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        long remain = jwtUtils.getRemainSeconds(accessToken);
        if (remain > 0) {
            redisTemplate.opsForValue().set(JwtAuthFilter.BLACKLIST_PREFIX + accessToken, "1",
                    remain, TimeUnit.SECONDS);
        }
    }
}
