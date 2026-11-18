package com.group.exam.common.enums;

import lombok.Getter;

/**
 * 业务错误码总表（API 文档 2.5）
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),

    // 10xxx 通用/鉴权
    PARAM_ERROR(10001, "参数校验失败"),
    UNAUTHORIZED(10002, "未登录或 token 无效/过期"),
    FORBIDDEN(10003, "无功能权限访问该接口"),
    DATA_SCOPE_DENIED(10004, "数据权限越权"),
    NOT_FOUND(10005, "资源不存在"),
    RATE_LIMITED(10006, "请求过于频繁"),
    DUPLICATE_SUBMIT(10007, "重复提交/幂等冲突"),
    FILE_TYPE_UNSUPPORTED(10008, "文件类型不支持"),
    FILE_TOO_LARGE(10009, "文件大小超限"),
    TOKEN_KICKED(10010, "token 已强制失效（异地登录被踢下线）"),
    SYSTEM_ERROR(10500, "系统内部错误"),

    // 20xxx 组织人员
    JOB_NO_EXISTS(20001, "工号已存在"),
    UNIT_CODE_EXISTS(20002, "单位编码已存在"),
    PARENT_UNIT_NOT_EXISTS(20003, "上级单位不存在"),
    UNIT_HAS_CHILDREN(20004, "单位下存在子级单位或在册人员，不可删除"),
    IMPORT_VALIDATION_FAILED(20005, "导入数据校验失败"),
    OLD_PASSWORD_WRONG(20006, "原密码错误"),
    ACCOUNT_LOCKED(20007, "账号已锁定"),

    // 30xxx 学习
    MATERIAL_NOT_FOUND(30001, "资料不存在或未发布"),
    MATERIAL_NOT_VISIBLE(30002, "资料不在本人可见范围内"),
    MATERIAL_AUDIT_STATUS_INVALID(30003, "资料当前审核状态不允许该操作"),
    LEARN_TASK_INVALID(30004, "学习任务不存在或已截止"),

    // 40xxx 题库试卷
    QUESTION_NOT_FOUND(40001, "题目不存在或已停用"),
    QUESTION_DUPLICATED(40002, "疑似重复题目"),
    PAPER_SCORE_MISMATCH(40003, "卷面总分校验失败（与设定总分不一致）"),
    PAPER_SEALED(40004, "试卷已被考试引用（已封存），不可修改，仅可复制新版本"),
    GRADE_RULE_INVALID(40005, "等级区间不连续或未完整覆盖 0~满分"),
    RANDOM_STRATEGY_INSUFFICIENT(40006, "抽题策略可用题量不足"),
    QUESTION_NOT_AUDITED(40007, "题目未审核启用，不可用于组卷"),

    // 50xxx 考试
    NOT_IN_EXAM_WINDOW(50001, "不在考试时间窗口内"),
    NOT_IN_CANDIDATE_LIST(50002, "当前用户不在本场考试考生名单内"),
    ALREADY_SUBMITTED(50003, "已交卷，不可重复提交"),
    SWITCH_LIMIT_EXCEEDED(50004, "切屏/离开页面次数超限"),
    OTHER_TERMINAL_ONLINE(50005, "账号已在其他终端在线答题"),
    SHEET_NOT_FOUND(50006, "答卷不存在"),
    SESSION_STATUS_INVALID(50007, "考试场次当前状态不允许该操作"),
    LATE_LIMIT_EXCEEDED(50008, "超过允许迟到进入时限"),
    SCORE_NOT_PUBLISHED(50009, "成绩尚未发布"),
    MARK_TASK_INVALID(50010, "阅卷任务已被领取或不在本人任务内"),

    // 60xxx 统计
    STAT_PARAM_ERROR(60001, "统计维度/时间范围参数错误"),
    EXPORT_FAILED(60002, "导出任务生成失败"),

    // 70xxx 练习/微信（V1.1）
    PRACTICE_QUES_INSUFFICIENT(70002, "可用题量不足"),
    WX_CODE_INVALID(70003, "微信登录凭证无效或已过期"),
    WX_NOT_BOUND(70004, "未绑定微信");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
