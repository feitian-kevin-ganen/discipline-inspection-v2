package com.group.exam.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * SYS-02 系统参数批量修改请求
 */
public class ConfigUpdateRequest {

    @NotEmpty(message = "configs 不能为空")
    @Valid
    private List<Item> configs;

    public List<Item> getConfigs() {
        return configs;
    }

    public void setConfigs(List<Item> configs) {
        this.configs = configs;
    }

    public static class Item {

        @NotBlank(message = "configKey 不能为空")
        private String configKey;

        @NotBlank(message = "configValue 不能为空")
        private String configValue;

        public String getConfigKey() {
            return configKey;
        }

        public void setConfigKey(String configKey) {
            this.configKey = configKey;
        }

        public String getConfigValue() {
            return configValue;
        }

        public void setConfigValue(String configValue) {
            this.configValue = configValue;
        }
    }
}
