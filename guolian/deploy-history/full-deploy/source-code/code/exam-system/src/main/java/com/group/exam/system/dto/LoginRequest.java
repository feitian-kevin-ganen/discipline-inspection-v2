package com.group.exam.system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AUTH-01 登录请求
 */
public class LoginRequest {

    @NotBlank(message = "工号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 终端标识：PC_ADMIN / PC_APP / H5 */
    private String terminal;

    private String deviceId;
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTerminal() {
        return terminal;
    }

    public void setTerminal(String terminal) {
        this.terminal = terminal;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
