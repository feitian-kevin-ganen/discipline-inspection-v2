package com.group.exam.exam.dto;

import java.io.Serializable;

/**
 * NOTIFY-WX-01 微信绑定请求
 *
 * <p>code 为小程序 wx.login() 临时登录凭证；开发模式（wx.mp.appid/secret 未配置真实值）
 * 直接接受 openid 字段写入；unbind=true 解绑。
 */
public class WechatBindReq implements Serializable {

    /** 小程序 wx.login() 返回的临时登录凭证 */
    private String code;

    /** 开发模式占位字段：未配置真实微信凭据时直接以此 openid 绑定 */
    private String openid;

    /** true 解绑（mp_openid/unionid 置空） */
    private Boolean unbind;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public Boolean getUnbind() {
        return unbind;
    }

    public void setUnbind(Boolean unbind) {
        this.unbind = unbind;
    }
}
