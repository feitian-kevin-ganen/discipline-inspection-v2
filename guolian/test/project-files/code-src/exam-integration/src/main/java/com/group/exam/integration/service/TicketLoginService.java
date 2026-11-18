package com.group.exam.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.exam.common.core.Result;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.integration.client.GuolianOpenApiClient;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.service.AuthService;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.OrgUserService;
import com.group.exam.system.service.SysLoginLogService;
import com.group.exam.system.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 国联 APP 单点登录（ticket 免密登录）服务
 * <p>
 * 流程：ticket → 调用公服 2.1.1 接口换取员工 ID → 按工号匹配本地用户 → 签发本系统 token。
 * 权限（菜单/按钮/数据）沿用本系统原有角色权限体系，通过 {@link AuthService#buildLoginUser} 构建。
 * </p>
 */
@Slf4j
@Service
public class TicketLoginService {

    private final GuolianOpenApiClient guolianOpenApiClient;
    private final OrgUserService orgUserService;
    private final OrgUnitService orgUnitService;
    private final AuthService authService;
    private final TokenService tokenService;
    private final SysLoginLogService sysLoginLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TicketLoginService(GuolianOpenApiClient guolianOpenApiClient,
                              OrgUserService orgUserService, OrgUnitService orgUnitService,
                              AuthService authService, TokenService tokenService,
                              SysLoginLogService sysLoginLogService) {
        this.guolianOpenApiClient = guolianOpenApiClient;
        this.orgUserService = orgUserService;
        this.orgUnitService = orgUnitService;
        this.authService = authService;
        this.tokenService = tokenService;
        this.sysLoginLogService = sysLoginLogService;
    }

    /**
     * 使用国联 APP 下发的 ticket 免密登录
     *
     * @param ticket    国联 APP 一次性 ticket
     * @param clientId  系统来源（为空用配置默认）
     * @param terminal  终端标识（H5/PC_ADMIN/PC_USER，仅用于登录日志，可空默认 H5）
     * @param ip        客户端 IP
     * @param userAgent 客户端 UA
     * @return 与普通登录一致的返回：accessToken/refreshToken/mustChangePassword/user
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> loginByTicket(String ticket, String clientId, String terminal, String ip, String userAgent) {
        if (terminal == null || terminal.isBlank()) {
            terminal = "H5";
        }
        // 1. 调公服 2.1.1 接口换取员工 ID
        String empId = resolveEmpId(ticket, clientId);

        // 2. 匹配本地用户：优先按国联员工ID(emp_id)，兼容本地工号直接相等的情况
        OrgUser user = orgUserService.getByEmpIdOrJobNo(empId);
        if (user == null) {
            sysLoginLogService.record(null, empId, ip, terminal, userAgent, false, "ticket免密登录：员工ID未绑定本地账号");
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "本平台无该员工账号，请联系管理员在国联员工ID中绑定");
        }
        if (user.getAccountStatus() != null && user.getAccountStatus() == 2) {
            sysLoginLogService.record(user.getId(), empId, ip, terminal, userAgent, false, "ticket免密登录：账号已锁定");
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getAccountStatus() != null && user.getAccountStatus() == 0) {
            sysLoginLogService.record(user.getId(), empId, ip, terminal, userAgent, false, "ticket免密登录：账号已停用");
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "账号已停用，请联系管理员");
        }

        // 3. 签发本系统 token（角色/数据权限沿用原体系）
        LoginUser loginUser = authService.buildLoginUser(user);
        Map<String, Object> tokens = tokenService.issueTokens(loginUser);

        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        orgUserService.updateById(user);
        sysLoginLogService.record(user.getId(), user.getJobNo(), ip, terminal, userAgent, true, "ticket免密登录");

        Map<String, Object> data = new LinkedHashMap<>(tokens);
        data.put("mustChangePassword", user.getAccountStatus() != null && user.getAccountStatus() == 3);
        data.put("user", userBrief(user, loginUser));
        log.info("ticket 免密登录成功: empId={} userId={}", empId, user.getId());
        return data;
    }

    /**
     * 解析公服 2.1.1 响应，提取员工 ID
     */
    private String resolveEmpId(String ticket, String clientId) {
        String body = guolianOpenApiClient.getEmpIdByTicket(ticket, clientId);
        try {
            JsonNode root = objectMapper.readTree(body);
            // 结构一（返回示例）：{"data":"4908","meta":{"success":true,"message":"...","status":200}}
            JsonNode meta = root.get("meta");
            if (meta != null && meta.has("success") && !meta.get("success").asBoolean()) {
                String msg = meta.hasNonNull("message") ? meta.get("message").asText() : "公服票据校验失败";
                throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), msg);
            }
            // 结构二（公共参数）：{"code":200,"success":true,"data":"4908","info":"..."}
            if (root.has("success") && !root.get("success").asBoolean()) {
                String msg = root.hasNonNull("info") ? root.get("info").asText()
                        : (root.hasNonNull("message") ? root.get("message").asText() : "公服票据校验失败");
                throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), msg);
            }
            JsonNode data = root.get("data");
            if (data == null || data.isNull()
                    || (data.isTextual() && data.asText().isBlank())
                    || (data.isNumber() && data.asLong() <= 0)) {
                throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "公服未返回有效的员工ID");
            }
            return data.isNumber() ? String.valueOf(data.asLong()) : data.asText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析公服 getEmpIdByTicket 响应失败: {}", e.getMessage(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "解析公服响应失败");
        }
    }

    private Map<String, Object> userBrief(OrgUser user, LoginUser loginUser) {
        OrgUnit unit = orgUnitService.getUnit(user.getOrgId());
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("userId", user.getId());
        brief.put("name", user.getUserName());
        brief.put("jobNo", user.getJobNo());
        brief.put("orgId", user.getOrgId());
        brief.put("orgName", unit == null ? null : unit.getUnitName());
        brief.put("roles", loginUser.getRoles());
        brief.put("avatarUrl", null);
        return brief;
    }
}
