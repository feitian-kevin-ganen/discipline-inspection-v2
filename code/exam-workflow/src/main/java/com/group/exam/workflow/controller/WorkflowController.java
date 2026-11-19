package com.group.exam.workflow.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.workflow.dto.FlowCompleteReq;
import com.group.exam.workflow.dto.FlowCopyVO;
import com.group.exam.workflow.dto.FlowDefinitionStateReq;
import com.group.exam.workflow.dto.FlowDefinitionVO;
import com.group.exam.workflow.dto.FlowDeployReq;
import com.group.exam.workflow.dto.FlowInstanceDetailVO;
import com.group.exam.workflow.dto.FlowInstanceVO;
import com.group.exam.workflow.dto.FlowMessageTriggerReq;
import com.group.exam.workflow.dto.FlowStartReq;
import com.group.exam.workflow.dto.FlowStopReq;
import com.group.exam.workflow.dto.FlowTaskVO;
import com.group.exam.workflow.dto.FlowTimeoutVO;
import com.group.exam.workflow.entity.FlowBizInstance;
import com.group.exam.workflow.entity.FlowDblink;
import com.group.exam.workflow.entity.FlowFormDef;
import com.group.exam.workflow.entity.FlowMessageRegistry;
import com.group.exam.workflow.entity.FlowTaskExt;
import com.group.exam.workflow.service.FlowInstanceService;
import com.group.exam.workflow.service.WorkflowCenterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 流程中心管理端接口（题目审核 / 资料审核共用）
 */
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowController {

    private static final String[] ADMIN_ROLES = {"SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN"};

    private final FlowInstanceService flowInstanceService;
    private final WorkflowCenterService workflowCenterService;

    public WorkflowController(FlowInstanceService flowInstanceService,
                              WorkflowCenterService workflowCenterService) {
        this.flowInstanceService = flowInstanceService;
        this.workflowCenterService = workflowCenterService;
    }

    @PostMapping("/start")
    public Result<FlowBizInstance> start(@Valid @RequestBody FlowStartReq req) {
        requireAdmin();
        return Result.ok(flowInstanceService.start(req.getBizType(), req.getBizId(), req.getTitle()));
    }

    @GetMapping("/task/todo")
    public Result<List<FlowTaskVO>> todo() {
        requireAdmin();
        return Result.ok(flowInstanceService.todoTasks());
    }

    @GetMapping("/task/done")
    public Result<List<FlowTaskVO>> done() {
        requireAdmin();
        return Result.ok(flowInstanceService.doneTasks());
    }

    @PostMapping("/task/{taskId}/complete")
    public Result<Void> complete(@PathVariable String taskId, @RequestBody FlowCompleteReq req) {
        requireAdmin();
        flowInstanceService.completeTask(taskId, req.isApprove(), req.getComment(), req.getCcUserIds());
        return Result.ok();
    }

    @GetMapping("/instance/my")
    public Result<List<FlowInstanceVO>> myStarted() {
        requireAdmin();
        return Result.ok(flowInstanceService.myStarted());
    }

    @GetMapping("/instance/all")
    public Result<PageResult<FlowInstanceVO>> allInstances(@RequestParam(required = false) String title,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        requireAdmin();
        return Result.ok(flowInstanceService.allInstances(title, status, pageNum, pageSize));
    }

    @PostMapping("/instance/{procInstId}/stop")
    public Result<Void> stop(@PathVariable String procInstId, @RequestBody(required = false) FlowStopReq req) {
        requireAdmin();
        flowInstanceService.stopInstance(procInstId, req == null ? null : req.getComment());
        return Result.ok();
    }

    @GetMapping("/instance/{procInstId}/diagram-data")
    public Result<Map<String, Object>> diagramData(@PathVariable String procInstId) {
        requireAdmin();
        return Result.ok(flowInstanceService.diagramData(procInstId));
    }

    @GetMapping("/instance/{procInstId}")
    public Result<FlowInstanceDetailVO> detail(@PathVariable String procInstId) {
        requireAdmin();
        return Result.ok(flowInstanceService.detail(procInstId));
    }

    @GetMapping("/definitions")
    public Result<List<FlowDefinitionVO>> definitions() {
        requireAdmin();
        return Result.ok(flowInstanceService.definitions());
    }

    @GetMapping("/definition/{definitionId}/xml")
    public Result<String> definitionXml(@PathVariable String definitionId) {
        requireAdmin();
        return Result.ok(flowInstanceService.definitionXml(definitionId));
    }

    /** 流程图渲染用 XML：旧版本无 BPMN DI 时回退到同 key 最新含图形的版本 */
    @GetMapping("/definition/{definitionId}/diagram-xml")
    public Result<String> diagramXml(@PathVariable String definitionId) {
        requireAdmin();
        return Result.ok(flowInstanceService.diagramXml(definitionId));
    }

    @PostMapping("/definition/{definitionId}/state")
    public Result<Void> changeDefinitionState(@PathVariable String definitionId,
                                              @RequestBody FlowDefinitionStateReq req) {
        requireAdmin();
        flowInstanceService.changeDefinitionState(definitionId, req.isSuspend());
        return Result.ok();
    }

    // ---------------------------------------------------------------- 抄送

    @GetMapping("/copy/my")
    public Result<PageResult<FlowCopyVO>> myCopies(@RequestParam(required = false) String readFlag,
                                                   @RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(workflowCenterService.myCopies(readFlag, pageNum, pageSize));
    }

    @GetMapping("/copy/unread-count")
    public Result<Long> unreadCopyCount() {
        return Result.ok(workflowCenterService.unreadCopyCount());
    }

    @PostMapping("/copy/{id}/read")
    public Result<Void> markCopyRead(@PathVariable Long id) {
        workflowCenterService.markCopyRead(id);
        return Result.ok();
    }

    @PostMapping("/copy/read-all")
    public Result<Integer> markAllCopyRead() {
        return Result.ok(workflowCenterService.markAllCopyRead());
    }

    // ---------------------------------------------------------------- 表单

    @GetMapping("/form/list")
    public Result<List<FlowFormDef>> listForms(@RequestParam(required = false) String name,
                                               @RequestParam(required = false) String procDefKey) {
        requireAdmin();
        return Result.ok(workflowCenterService.listForms(name, procDefKey));
    }

    @PostMapping("/form")
    public Result<FlowFormDef> saveForm(@RequestBody FlowFormDef form) {
        requireAdmin();
        return Result.ok(workflowCenterService.saveForm(form));
    }

    @DeleteMapping("/form/{id}")
    public Result<Void> deleteForm(@PathVariable Long id) {
        requireAdmin();
        workflowCenterService.deleteForm(id);
        return Result.ok();
    }

    @PostMapping("/form/{id}/status")
    public Result<Void> changeFormStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireAdmin();
        workflowCenterService.changeFormStatus(id, body.get("status"));
        return Result.ok();
    }

    // ---------------------------------------------------------------- 数据源

    @GetMapping("/dblink/list")
    public Result<List<FlowDblink>> listDblinks(@RequestParam(required = false) String name) {
        requireAdmin();
        return Result.ok(workflowCenterService.listDblinks(name));
    }

    @PostMapping("/dblink")
    public Result<FlowDblink> saveDblink(@RequestBody FlowDblink dblink) {
        requireAdmin();
        return Result.ok(workflowCenterService.saveDblink(dblink));
    }

    @DeleteMapping("/dblink/{id}")
    public Result<Void> deleteDblink(@PathVariable Long id) {
        requireAdmin();
        workflowCenterService.deleteDblink(id);
        return Result.ok();
    }

    @PostMapping("/dblink/test")
    public Result<String> testDblink(@RequestBody FlowDblink dblink) {
        requireAdmin();
        return Result.ok(workflowCenterService.testDblink(dblink));
    }

    // ---------------------------------------------------------------- 消息注册

    @GetMapping("/message/list")
    public Result<List<FlowMessageRegistry>> listMessages() {
        requireAdmin();
        return Result.ok(workflowCenterService.listMessages());
    }

    @PostMapping("/message")
    public Result<FlowMessageRegistry> saveMessage(@RequestBody FlowMessageRegistry registry) {
        requireAdmin();
        return Result.ok(workflowCenterService.saveMessage(registry));
    }

    @DeleteMapping("/message/{id}")
    public Result<Void> deleteMessage(@PathVariable Long id) {
        requireAdmin();
        workflowCenterService.deleteMessage(id);
        return Result.ok();
    }

    @PostMapping("/message/trigger")
    public Result<Integer> triggerMessage(@RequestBody FlowMessageTriggerReq req) {
        requireAdmin();
        return Result.ok(workflowCenterService.triggerMessage(req.getMessageRef(), req.getProcInstId()));
    }

    // ---------------------------------------------------------------- 超时与催办

    @GetMapping("/monitor/task-ext")
    public Result<List<FlowTaskExt>> listTaskExts(@RequestParam(required = false) String procDefId) {
        requireAdmin();
        return Result.ok(workflowCenterService.listTaskExts(procDefId));
    }

    @PostMapping("/monitor/task-ext")
    public Result<FlowTaskExt> saveTaskExt(@RequestBody FlowTaskExt ext) {
        requireAdmin();
        return Result.ok(workflowCenterService.saveTaskExt(ext));
    }

    @DeleteMapping("/monitor/task-ext/{id}")
    public Result<Void> deleteTaskExt(@PathVariable Long id) {
        requireAdmin();
        workflowCenterService.deleteTaskExt(id);
        return Result.ok();
    }

    @GetMapping("/monitor/timeout")
    public Result<PageResult<FlowTimeoutVO>> timeoutTasks(@RequestParam(defaultValue = "1") long pageNum,
                                                          @RequestParam(defaultValue = "10") long pageSize) {
        requireAdmin();
        return Result.ok(workflowCenterService.timeoutTasks(pageNum, pageSize));
    }

    @PostMapping("/monitor/urge/{taskId}")
    public Result<String> urge(@PathVariable String taskId) {
        requireAdmin();
        return Result.ok(workflowCenterService.urge(taskId));
    }

    // ---------------------------------------------------------------- 设计器

    @GetMapping("/designer/list")
    public Result<List<FlowDefinitionVO>> designerList() {
        requireAdmin();
        return Result.ok(workflowCenterService.designerList());
    }

    @GetMapping("/designer/xml/{key}")
    public Result<String> designerXml(@PathVariable String key) {
        requireAdmin();
        return Result.ok(workflowCenterService.definitionXmlByKey(key));
    }

    @PostMapping("/designer/deploy")
    public Result<String> deploy(@RequestBody FlowDeployReq req) {
        requireAdmin();
        return Result.ok(workflowCenterService.deploy(req.getName(), req.getXml()));
    }

    private void requireAdmin() {
        SecurityUtils.requireAnyRole(ADMIN_ROLES);
    }
}
