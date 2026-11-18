package com.group.exam.system.controller;

import com.group.exam.common.core.Result;
import com.group.exam.system.dto.LoginRequest;
import com.group.exam.system.dto.PasswordChangeRequest;
import com.group.exam.system.dto.RefreshRequest;
import com.group.exam.system.service.AuthService;
import com.group.exam.system.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AUTH 认证（API 文档 4.1）
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** AUTH-01 登录（白名单） */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginRequest request,
                                             HttpServletRequest httpRequest) {
        return Result.ok(authService.login(request, IpUtils.getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }

    /** AUTH-02 登出 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest,
                               @RequestBody(required = false) Map<String, String> body) {
        String refreshToken = body != null ? body.get("refreshToken") : null;
        if (!StringUtils.hasText(refreshToken)) {
            refreshToken = httpRequest.getHeader("X-Refresh-Token");
        }
        authService.logout(resolveBearer(httpRequest), refreshToken);
        return Result.ok();
    }

    /** AUTH-03 刷新 Token（白名单，凭 refreshToken） */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@Validated @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.getRefreshToken()));
    }

    /** AUTH-04 修改本人密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody PasswordChangeRequest request,
                                       HttpServletRequest httpRequest) {
        authService.changePassword(request, resolveBearer(httpRequest));
        return Result.ok();
    }

    /** AUTH-05 当前用户信息、角色与权限菜单 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.me());
    }

    private String resolveBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
