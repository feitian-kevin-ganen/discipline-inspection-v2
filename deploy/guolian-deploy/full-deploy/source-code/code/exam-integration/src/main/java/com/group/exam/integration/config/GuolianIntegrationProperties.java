package com.group.exam.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部系统对接配置（数字国联 APP 开放平台）
 */
@Data
@Component
@ConfigurationProperties(prefix = "exam.integration.guolian")
public class GuolianIntegrationProperties {

    /**
     * 开放平台域名（测试环境: http://10.20.8.8:32389，生产环境换 IP 即可）
     */
    private String baseUrl = "http://10.20.8.8:32389";

    /**
     * 系统来源标识（clientId）
     */
    private String clientId = "guolian-jijian";

    /**
     * 能开平台 SSO 接口请求头 x-gateway-host（测试环境值，生产环境更换）
     */
    private String gatewayHost = "dflw9c81mnwwnejj.apigateway.inc.alipay.net";

    /**
     * 应用 AccessKey（开发者门户创建应用后生成；为空则按"无需鉴权"模式调用）
     */
    private String accessKey = "yv1ftGWERni3gTzN";

    /**
     * 应用 SecretKey（开发者门户创建应用后生成；为空则按"无需鉴权"模式调用）
     */
    private String secretKey = "tey40AwC64S1NJFS2K1FZ9yeG5dDKmK2";

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeoutMs = 5000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeoutMs = 10000;
}
