package com.group.exam.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 工具（jjwt 0.12.x）
 */
@Component
public class JwtUtils {

    @Value("${exam.jwt.secret}")
    private String secret;

    @Value("${exam.jwt.access-expire-seconds:7200}")
    private long accessExpireSeconds;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long getAccessExpireSeconds() {
        return accessExpireSeconds;
    }

    public String createAccessToken(LoginUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.getUserId());
        claims.put("jobNo", user.getJobNo());
        claims.put("userName", user.getUserName());
        claims.put("orgId", user.getOrgId());
        claims.put("orgPath", user.getOrgPath());
        claims.put("roles", user.getRoles());
        claims.put("dataScope", user.getDataScope());
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(user.getUserId()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpireSeconds * 1000))
                .signWith(key())
                .compact();
    }

    /**
     * 解析 accessToken，无效/过期返回 null
     */
    @SuppressWarnings("unchecked")
    public LoginUser parseAccessToken(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
            LoginUser u = new LoginUser();
            u.setUserId(toLong(c.get("uid")));
            u.setJobNo(c.get("jobNo", String.class));
            u.setUserName(c.get("userName", String.class));
            u.setOrgId(toLong(c.get("orgId")));
            u.setOrgPath(c.get("orgPath", String.class));
            u.setRoles((List<String>) c.get("roles"));
            Object ds = c.get("dataScope");
            u.setDataScope(ds == null ? null : ((Number) ds).intValue());
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 剩余有效期（秒），用于登出黑名单 TTL；解析失败返回 0
     */
    public long getRemainSeconds(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
            long remain = (c.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remain, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }
}
