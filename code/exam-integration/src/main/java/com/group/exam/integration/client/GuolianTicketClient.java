package com.group.exam.integration.client;

/**
 * 国联 ticket 换取员工ID 的客户端抽象。
 * <p>
 * 目标机使用 {@link GuolianOpenApiClient}（真实公服接口）；
 * 本机（profile=local）使用 {@link MockGuolianTicketClient}（模拟 ticket，便于无门户环境联调）。
 * </p>
 */
public interface GuolianTicketClient {

    /**
     * 根据 ticket 换取员工 ID
     *
     * @param ticket   一次性 ticket
     * @param clientId 系统来源（为空使用配置默认值）
     * @return 公服结构 JSON 字符串，成功时 data 为员工 ID
     */
    String getEmpIdByTicket(String ticket, String clientId);
}
