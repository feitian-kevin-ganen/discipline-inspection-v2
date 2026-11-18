package com.group.exam.integration.portal;

import com.group.exam.common.core.Result;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 本机模拟国联门户登录接口（profile=local，测试机不注册）。
 */
@RestController
@RequestMapping("/api/v1/portal")
@Profile("local")
public class LocalPortalController {

    private final LocalPortalService localPortalService;

    public LocalPortalController(LocalPortalService localPortalService) {
        this.localPortalService = localPortalService;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of("mockPortal", true));
    }

    /**
     * 手机号 + 密码登录（密码不校验），返回一次性 ticket 与账号信息。
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String password = request.get("password");
        if (phone == null || phone.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "手机号不能为空");
        }
        return Result.ok(localPortalService.login(phone, password));
    }
}
