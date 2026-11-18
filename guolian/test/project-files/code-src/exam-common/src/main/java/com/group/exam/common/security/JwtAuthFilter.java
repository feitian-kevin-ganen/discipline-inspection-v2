package com.group.exam.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 鉴权过滤器：解析 Bearer token，构建登录用户上下文
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 登出黑名单 key 前缀（auth.logout 时写入） */
    public static final String BLACKLIST_PREFIX = "exam:blacklist:";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthFilter(JwtUtils jwtUtils, StringRedisTemplate redisTemplate) {
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if ((!StringUtils.hasText(header) || !header.startsWith("Bearer "))
                && StringUtils.hasText(request.getParameter("access_token"))) {
            // <video>/<img> 等媒体标签无法携带 Authorization 头，允许通过 access_token 查询参数鉴权（仅限媒体流场景使用）
            header = "Bearer " + request.getParameter("access_token");
        }
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Boolean blacklisted = redisTemplate.hasKey(BLACKLIST_PREFIX + token);
            if (!Boolean.TRUE.equals(blacklisted)) {
                LoginUser user = jwtUtils.parseAccessToken(token);
                if (user != null) {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (user.getRoles() != null) {
                        user.getRoles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                    }
                    if (user.isAdminSide()) {
                        authorities.add(new SimpleGrantedAuthority("ADMIN"));
                    }
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
