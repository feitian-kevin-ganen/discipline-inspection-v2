package com.group.exam.integration.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 本机模拟公服（profile=local）：ticket 从本地门户签发时写入的 Redis 键中读取。
 * <p>
 * 键：portal:ticket:{ticket} → empId，TTL 5 分钟，读取即删除（一次性）。
 * </p>
 */
@Slf4j
@Component
@Primary
@Profile("local")
public class MockGuolianTicketClient implements GuolianTicketClient {

    private static final String TICKET_KEY_PREFIX = "portal:ticket:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MockGuolianTicketClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getEmpIdByTicket(String ticket, String clientId) {
        String key = TICKET_KEY_PREFIX + ticket;
        String empId = redisTemplate.opsForValue().get(key);
        if (empId != null) {
            redisTemplate.delete(key);
        }
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode meta = root.putObject("meta");
        if (empId == null) {
            log.warn("模拟 ticket 无效或已使用: {}", ticket);
            meta.put("success", false);
            meta.put("message", "模拟ticket无效或已使用");
            root.putNull("data");
        } else {
            meta.put("success", true);
            root.put("data", empId);
            log.info("模拟 ticket 换取员工ID: ticket={} empId={}", ticket, empId);
        }
        return root.toString();
    }
}
