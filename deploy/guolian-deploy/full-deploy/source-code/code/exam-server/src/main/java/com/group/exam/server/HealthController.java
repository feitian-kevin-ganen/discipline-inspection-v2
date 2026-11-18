package com.group.exam.server;

import com.group.exam.common.core.Result;
import com.group.exam.common.util.DbDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查（白名单）
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of("status", "UP"));
    }

    /** 数据库探针：方言无关，PG 与 PolarDB-X/MySQL 均可执行 */
    @GetMapping("/health/db")
    public Result<Map<String, Object>> dbProbe() {
        Map<String, Object> info = new HashMap<>();
        String version = jdbcTemplate.queryForObject("select version()", String.class);
        info.put("version", version);
        info.put("currentDatabase", mysqlDialect()
                ? jdbcTemplate.queryForObject("select database()", String.class)
                : jdbcTemplate.queryForObject("select current_database()", String.class));
        info.put("adminCount", jdbcTemplate.queryForObject(
                "select count(*) from t_org_user where deleted=0 and job_no='admin'", Integer.class));
        info.put("userTotal", jdbcTemplate.queryForObject("select count(*) from t_org_user", Integer.class));
        return Result.ok(info);
    }

    private boolean mysqlDialect() {
        return DbDialect.isMysql(jdbcTemplate.getDataSource());
    }
}
