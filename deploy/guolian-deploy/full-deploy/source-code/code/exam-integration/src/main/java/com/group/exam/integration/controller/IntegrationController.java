package com.group.exam.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.exam.common.core.Result;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.integration.client.GuolianTicketClient;
import com.group.exam.integration.service.TicketLoginService;
import com.group.exam.system.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 外部系统对接接口（数字国联 APP 开放平台）
 * <p>
 * 2.1.1 解密员工 ticket：透传调用 {域名}/open-api/center/anon/user/getEmpIdByTicket
 * </p>
 */
@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final GuolianTicketClient guolianTicketClient;
    private final TicketLoginService ticketLoginService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 2.1.1 根据 ticket 换取员工 ID（与公服接口同名；旧路径 getEmpId 保留兼容）
     *
     * @param ticket   用户的 ticket（必填）
     * @param clientId 系统来源（选填，默认取配置 exam.integration.guolian.client-id）
     * @return 外部系统原始报文，成功时 data 为员工 ID
     */
    @GetMapping({"/ticket/getEmpIdByTicket", "/ticket/getEmpId"})
    public Result<JsonNode> getEmpIdByTicket(@RequestParam String ticket,
                                             @RequestParam(required = false) String clientId) throws Exception {
        String body = guolianTicketClient.getEmpIdByTicket(ticket, clientId);
        return Result.ok(objectMapper.readTree(body));
    }

    /**
     * 国联 APP 单点免密登录：ticket → 员工ID → 本地账号 → 签发 token
     *
     * @param request 请求体（ticket 必填，clientId 选填）
     * @return 与普通登录一致的返回：accessToken/refreshToken/mustChangePassword/user
     */
    @PostMapping("/ticket/login")
    public Result<Map<String, Object>> loginByTicket(@RequestBody Map<String, String> request,
                                                     HttpServletRequest httpRequest) {
        String ticket = request.get("ticket");
        String clientId = request.get("clientId");
        String terminal = request.get("terminal");
        if (ticket == null || ticket.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "ticket 不能为空");
        }
        return Result.ok(ticketLoginService.loginByTicket(
                ticket.trim(), clientId, terminal, IpUtils.getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }
}
