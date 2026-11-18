package com.group.exam.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.system.entity.SysConfig;
import com.group.exam.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

/**
 * 系统参数服务（供全系统读取参数）
 */
@Service
public class SysConfigService extends ServiceImpl<SysConfigMapper, SysConfig> {

    public String getValue(String key) {
        SysConfig config = getOne(new QueryWrapper<SysConfig>().eq("config_key", key), false);
        return config == null ? null : config.getConfigValue();
    }

    public int getInt(String key, int def) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return def;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public double getDouble(String key, double def) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
