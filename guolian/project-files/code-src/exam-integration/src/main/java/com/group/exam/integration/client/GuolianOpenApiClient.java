package com.group.exam.integration.client;

import com.alibaba.fastjson.JSON;
import com.alipay.sofa.gateway.api.client.ApiClient;
import com.alipay.sofa.gateway.api.client.DefaultApiClient;
import com.alipay.sofa.gateway.api.domain.ApiSecretKey;
import com.alipay.sofa.gateway.api.domain.request.GwSdkApiRequest;
import com.alipay.sofa.gateway.api.domain.response.ApiResponse;
import com.alipay.sofa.gateway.api.exception.ApiException;
import com.alipay.sofa.gateway.api.internal.parser.json.ObjectStringParser;
import com.group.exam.integration.config.GuolianIntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 数字国联 APP 开放平台客户端
 * <p>
 * 基于能开平台网关 SDK（gateway-sdk-2.6.8）实现，
 * 对接文档：《东西向流量子系统快速对接手册 1.3.0》2.1.1 / 3.2 节。
 * </p>
 */
@Slf4j
@Component
public class GuolianOpenApiClient {

    private static final String GET_EMP_ID_BY_TICKET_PATH = "open-api/center/anon/user/getEmpIdByTicket";

    private final GuolianIntegrationProperties properties;
    private final ApiClient apiClient;

    public GuolianOpenApiClient(GuolianIntegrationProperties properties) {
        this.properties = properties;
        // 需要鉴权时使用 AK/SK 初始化；未配置 AK/SK 时按"无需鉴权"模式初始化
        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            List<ApiSecretKey> secretKeys = new ArrayList<>();
            secretKeys.add(new ApiSecretKey(properties.getAccessKey(), properties.getSecretKey()));
            this.apiClient = new DefaultApiClient(properties.getBaseUrl(), secretKeys);
            log.info("国联开放平台客户端已初始化（AK/SK 鉴权模式）: {}", properties.getBaseUrl());
        } else {
            this.apiClient = new DefaultApiClient(properties.getBaseUrl());
            log.info("国联开放平台客户端已初始化（无需鉴权模式）: {}", properties.getBaseUrl());
        }
    }

    /**
     * 2.1.1 / 3.2 解密员工 ticket：GET {域名}/open-api/center/anon/user/getEmpIdByTicket
     *
     * @param ticket   用户 ticket
     * @param clientId 系统来源（为空时使用配置的默认 clientId）
     * @return 外部系统响应 JSON 字符串（成功时 data 为员工 ID）
     */
    public String getEmpIdByTicket(String ticket, String clientId) {
        GwSdkApiRequest request = new GwSdkApiRequest();
        // 必填，设置路径
        request.setPath(GET_EMP_ID_BY_TICKET_PATH);
        // 必填，设置为 GET 请求
        request.setRequestType("GET");
        // 必填，设置网关的 header（能开平台 SSO 接口 x-gateway-host）
        request.getHeaderParams().put("x-gateway-host", properties.getGatewayHost());
        // 文档 2.1.2 要求：请求参数放 Headers 里（clientId、ticket）；query 同步携带一份以兼容不同实现
        String cid = StringUtils.hasText(clientId) ? clientId : properties.getClientId();
        request.getHeaderParams().put("clientId", cid);
        request.getHeaderParams().put("ticket", ticket);
        request.getBizParams().put("clientId", cid);
        request.getBizParams().put("ticket", ticket);
        // 选填，是否对响应进行签名校验
        request.setClientCheckSign(false);

        try {
            log.info("调用国联开放平台 getEmpIdByTicket: path={} clientId={} ticket={}",
                    GET_EMP_ID_BY_TICKET_PATH, cid, ticket);
            ApiResponse response = apiClient.execute(request,
                    new ObjectStringParser<>(request.getResponseClass()));
            if (response.getResponseCode() != 200) {
                log.error("国联开放平台 getEmpIdByTicket 请求失败，错误码：{} 返回内容：{}",
                        response.getResponseCode(), response.getBody());
                throw new ApiException("国联开放平台请求失败，错误码：" + response.getResponseCode()
                        + "，返回内容：" + response.getBody());
            }
            return response.getBody();
        } catch (ApiException e) {
            log.error("调用国联开放平台 getEmpIdByTicket 失败: {}", e.getMessage(), e);
            throw new RuntimeException("调用国联开放平台接口失败: " + e.getMessage(), e);
        }
    }
}
