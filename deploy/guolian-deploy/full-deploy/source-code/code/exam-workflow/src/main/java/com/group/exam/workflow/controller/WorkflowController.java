package com.group.exam.workflow.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.workflow.dto.FlowCompleteReq;
import com.group.exam.workflow.dto.FlowDefinitionStateReq;
import com.group.exam.workflow.dto.FlowDefinitionVO;
import com.group.exam.workflow.dto.FlowInstanceDetailVO;
import com.group.exam.workflow.dto.FlowInstanceVO;
import com.group.exam.workflow.dto.FlowStartReq;
import com.group.exam.workflow.dto.FlowStopReq;
import com.group.exam.workflow.dto.FlowTaskVO;
import com.group.exam.workflow.entity.FlowBizInstance;
import com.group.exam.workflow.service.FlowInstanceService;
import jakarta.validation.Valid;
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
 * 审批流程管理端接口（题目审核 / 资料审核共用）
 */
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowController {

    private static final String[] ADMIN_ROLES = {"SUPER_ADMIN", "HQ_ADMIN", "L2_ADMIN", "SUB_ADMIN"};

    private final FlowInstanceService flowInstanceService;

    public WorkflowController(FlowInstanceService flowInstanceService) {
        this.flowInstanceService = flowInstanceService;
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
        flowInstanceService.completeTask(taskId, req.isApprove(), req.getComment());
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

    @PostMapping("/definition/{definitionId}/state")
    public Result<Void> changeDefinitionState(@PathVariable String definitionId,
                                              @RequestBody FlowDefinitionStateReq req) {
        requireAdmin();
        flowInstanceService.changeDefinitionState(definitionId, req.isSuspend());
        return Result.ok();
    }

    private void requireAdmin() {
        SecurityUtils.requireAnyRole(ADMIN_ROLES);
    }
}
