package com.group.exam.workflow.listener;

import com.group.exam.workflow.service.FlowInstanceService;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEventDispatcher;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后把结束监听器挂到流程引擎事件分发器上（引擎初始化早于业务 Bean，故用 Runner 注册）
 */
@Component
public class FlowEventRegistrar implements ApplicationRunner {

    private final ProcessEngine processEngine;
    private final FlowInstanceService flowInstanceService;

    public FlowEventRegistrar(ProcessEngine processEngine, FlowInstanceService flowInstanceService) {
        this.processEngine = processEngine;
        this.flowInstanceService = flowInstanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!(processEngine.getProcessEngineConfiguration() instanceof ProcessEngineConfigurationImpl configuration)) {
            return;
        }
        FlowableEventDispatcher dispatcher = configuration.getEventDispatcher();
        if (dispatcher == null) {
            return;
        }
        dispatcher.addEventListener(new FlowEndListener(flowInstanceService),
                FlowableEngineEventType.PROCESS_COMPLETED,
                FlowableEngineEventType.PROCESS_CANCELLED);
    }
}
