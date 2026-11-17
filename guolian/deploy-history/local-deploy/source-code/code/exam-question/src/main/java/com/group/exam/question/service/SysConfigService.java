package com.group.exam.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.question.entity.SysConfig;
import com.group.exam.question.mapper.QuesSysConfigMapper;
import org.springframework.stereotype.Service;

/**
 * 系统参数只读访问（t_sys_config）。
 * exam-question 仅依赖 exam-common，故在本模块内做轻量只读实现。
 */
@Service("quesSysConfigService")
public class SysConfigService extends ServiceImpl<QuesSysConfigMapper, SysConfig> {

    /** 题目是否需要审核（ques.auditRequired），默认 true */
    public static final String KEY_QUES_AUDIT_REQUIRED = "ques.auditRequired";

    /** 查重相似度阈值（ques.dup.threshold），默认 0.8 */
    public static final String KEY_DUP_THRESHOLD = "ques.dup.threshold";

    /** 查重强制阻断开关（ques.dup.block），默认 false */
    public static final String KEY_DUP_BLOCK = "ques.dup.block";

    public String getValue(String key) {
        SysConfig config = getOne(new QueryWrapper<SysConfig>()
                .select("id", "config_key", "config_value")
                .eq("config_key", key)
                .last("LIMIT 1"), false);
        return config == null ? null : config.getConfigValue();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String v = value.trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    public double getDouble(String key, double defaultValue) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 题目新增/导入后的初始状态：需审核→1待审，免审→2启用 */
    public int initialItemStatus() {
        return getBoolean(KEY_QUES_AUDIT_REQUIRED, true) ? 1 : 2;
    }

    public boolean isAuditRequired() {
        return getBoolean(KEY_QUES_AUDIT_REQUIRED, true);
    }
}
