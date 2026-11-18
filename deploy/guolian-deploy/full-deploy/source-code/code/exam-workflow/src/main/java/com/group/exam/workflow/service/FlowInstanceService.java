package com.group.exam.workflow.service;

import com.group.exam.common.core.PageResult;
import com.group.exam.workflow.dto.FlowDefinitionVO;
import com.group.exam.workflow.dto.FlowInstanceDetailVO;
import com.group.exam.workflow.dto.FlowInstanceVO;
import com.group.exam.workflow.dto.FlowTaskVO;
import com.group.exam.workflow.entity.FlowBizInstance;

import java.util.List;
import java.util.Map;

/**
 * 审批流程能力：发起、待办/已办、办理、我发起的、流转历史。
 * 业务模块只依赖本接口，不直接使用 Flowable API。
 */
public interface FlowInstanceService {

    String REJECT_PREFIX = "REJECT:";
    String STOP_PREFIX = "STOP:";

    /** 发起流程（幂等：同一单据已有进行中实例时直接返回） */
    FlowBizInstance start(String bizType, Long bizId, String title);

    boolean hasRunning(String bizType, Long bizId);

    FlowBizInstance findRunning(String bizType, Long bizId);

    /** 按业务单据办理当前任务：approve=true 通过并结束，false 驳回并结束 */
    void completeByBiz(String bizType, Long bizId, boolean approve, String comment);

    /** 终止业务单据的进行中流程（停用/删除单据时调用） */
    void cancelByBiz(String bizType, Long bizId, String comment);

    /** 按任务 id 办理 */
    void completeTask(String taskId, boolean approve, String comment);

    List<FlowTaskVO> todoTasks();

    List<FlowTaskVO> doneTasks();

    List<FlowInstanceVO> myStarted();

    FlowInstanceDetailVO detail(String procInstId);

    List<FlowDefinitionVO> definitions();

    /** 流程监控：全部实例（管理员视角，支持标题/状态过滤） */
    PageResult<FlowInstanceVO> allInstances(String title, String status, long pageNum, long pageSize);

    /** 终止进行中的流程实例（仅 RUNNING） */
    void stopInstance(String procInstId, String comment);

    /** 流程图高亮数据：finished / active / rejected 节点 id 集合 */
    Map<String, Object> diagramData(String procInstId);

    /** 流程定义 BPMN XML（流程设计图 / 查看 XML） */
    String definitionXml(String definitionId);

    /** 挂起/激活流程定义（级联到进行中实例） */
    void changeDefinitionState(String definitionId, boolean suspend);

    /**
     * 流程结束/取消回调入口（由 Flowable 事件监听器调用）
     *
     * @param cancelled true=流程被删除（驳回/终止），false=正常结束
     * @param reason    删除原因，含 REJECT: / STOP: 前缀
     */
    void handleEnded(String procInstId, boolean cancelled, String reason);
}
