package com.group.exam.workflow.support;

/**
 * 审批动作上下文：Flowable 事件监听器与业务调用在同一线程内同步执行，
 * 借 ThreadLocal 把「操作人 / 审批意见」传给结束监听器，避免额外落库。
 */
public final class FlowContext {

    private static final ThreadLocal<Long> OPERATOR = new ThreadLocal<>();
    private static final ThreadLocal<String> COMMENT = new ThreadLocal<>();

    private FlowContext() {
    }

    public static void set(Long operatorId, String comment) {
        OPERATOR.set(operatorId);
        COMMENT.set(comment);
    }

    public static Long getOperator() {
        return OPERATOR.get();
    }

    public static String getComment() {
        return COMMENT.get();
    }

    public static void clear() {
        OPERATOR.remove();
        COMMENT.remove();
    }
}
