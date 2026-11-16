package com.group.exam.common.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.group.exam.common.mybatis.PgLocalDateTimeTypeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件 + TIMESTAMPTZ TypeHandler。
 * 注意：JSONB 系列 TypeHandler 由实体字段 @TableField(typeHandler=...) 显式指定，
 * 不能放入 type-handlers-package 全局扫描（其中 Object 类型处理器会劫持所有 Wrapper 参数绑定）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 不指定 DbType：按连接自动识别方言，同时兼容 PostgreSQL（本地）与 PolarDB-X/MySQL（目标环境）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /** 全局注册 TIMESTAMPTZ ↔ LocalDateTime 处理器 */
    @Bean
    public ConfigurationCustomizer pgTypeHandlerCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry().register(PgLocalDateTimeTypeHandler.class);
    }
}
