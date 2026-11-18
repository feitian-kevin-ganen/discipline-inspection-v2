package com.group.exam.workflow.spi;

/**
 * 业务流程状态回调。实现类放在业务模块中，Bean 名称必须等于 bizType（流程定义 key），
 * 引擎按 bizType 查找对应实现；未实现的业务类型不做回调。
 */
public interface FlowStatusCallback {

    /** 流程发起成功（单据进入待审） */
    default void onStart(String bizType, Long bizId, String procInstId, Long startUserId) {
    }

    /** 审批通过（流程正常结束） */
    default void onPass(String bizType, Long bizId, String procInstId, String comment, Long operatorId) {
    }

    /** 审批驳回（流程被删除，原因前缀 REJECT:） */
    default void onReject(String bizType, Long bizId, String procInstId, String comment, Long operatorId) {
    }

    /** 流程终止（业务停用/删除等，原因前缀 STOP:） */
    default void onStop(String bizType, Long bizId, String procInstId, String comment, Long operatorId) {
    }
}
