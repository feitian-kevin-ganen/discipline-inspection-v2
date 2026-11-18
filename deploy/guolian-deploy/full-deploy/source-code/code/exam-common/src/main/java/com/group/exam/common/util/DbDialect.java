package com.group.exam.common.util;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库方言判定。
 * <p>本机与测试机均为 MySQL，历史环境为 PostgreSQL；MySQL 8 的 {@code version()} 只返回版本号，
 * 不能用它判断方言，统一读取 JDBC 连接元数据。
 */
public final class DbDialect {

    private DbDialect() {
    }

    public static boolean isMysql(DataSource dataSource) {
        if (dataSource == null) {
            return true;
        }
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("mysql");
        } catch (SQLException e) {
            return true;
        }
    }
}
