package com.group.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.group.exam.common.mybatis.AbstractJsonbTypeHandler.JsonbMapHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 微信推送记录（t_notify_wx_log，V1.1 新增，每次推送一行、只增不改，无 update_by/update_time/deleted 字段）
 *
 * <p>scene：1考试发布 2考前提醒 3截止前提醒 4成绩公布；channel：1小程序订阅消息 2公众号模板消息(预留)；
 * send_status：0待发送 1成功 2失败 3放弃（订阅次数耗尽/用户未绑定）；失败重试追加新行并以 retry_of 关联。
 */
@Data
@TableName(value = "t_notify_wx_log", autoResultMap = true)
public class NotifyWxLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联站内通知（t_sys_notice.id，可为空表示仅微信通道） */
    private Long noticeId;

    /** 通知场景：1考试发布 2考前提醒 3截止前提醒 4成绩公布 */
    private Integer scene;

    /** 微信通道：1小程序订阅消息 2公众号模板消息（预留） */
    private Integer channel;

    /** 接收人（t_org_user.id） */
    private Long receiverId;

    /** 推送目标 openid 快照，防解绑后无法溯源 */
    private String mpOpenid;

    /** 关联业务：session 等 */
    private String bizType;

    private Long bizId;

    /** 微信消息模板 id */
    private String templateId;

    /** 模板占位数据快照（如考试名称/时间/成绩，不含题目答案等敏感数据） */
    @TableField(typeHandler = JsonbMapHandler.class)
    private Map<String, Object> msgData;

    /** 发送状态：0待发送 1成功 2失败 3放弃 */
    private Integer sendStatus;

    private Integer errcode;

    private String errmsg;

    /** 重试来源记录 id（首行为 NULL） */
    private Long retryOf;

    private LocalDateTime sendTime;

    private LocalDateTime createTime;
}
