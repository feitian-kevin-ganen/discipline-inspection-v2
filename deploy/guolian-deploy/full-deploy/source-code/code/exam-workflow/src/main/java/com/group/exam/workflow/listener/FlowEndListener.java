package com.group.exam.workflow.listener;

import com.group.exam.workflow.service.FlowInstanceService;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.delegate.event.FlowableCancelledEvent;

/**
 * 流程结束监听：正常结束 → 通过；删除实例 → 驳回/终止（删除原因带前缀区分）
 */
public class FlowEndListener implements FlowableEventListener {

    private final FlowInstanceService flowInstanceService;

    public FlowEndListener(FlowInstanceService flowInstanceService) {
        this.flowInstanceService = flowInstanceService;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == FlowableEngineEventType.PROCESS_COMPLETED) {
            flowInstanceService.handleEnded(processInstanceIdOf(event), false, null);
            return;
        }
        if (event.getType() == FlowableEngineEventType.PROCESS_CANCELLED
                && event instanceof FlowableCancelledEvent cancelled) {
            Object cause = cancelled.getCause();
            flowInstanceService.handleEnded(cancelled.getProcessInstanceId(), true,
                    cause == null ? null : String.valueOf(cause));
        }
    }

    private String processInstanceIdOf(FlowableEvent event) {
        if (event instanceof org.flowable.common.engine.api.delegate.event.FlowableEngineEvent engineEvent) {
            return engineEvent.getProcessInstanceId();
        }
        return null;
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
