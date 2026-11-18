package com.group.exam.workflow.controller;

import com.group.exam.common.core.Result;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.workflow.dto.FlowCompleteReq;
import com.group.exam.workflow.dto.FlowDefinitionVO;
import com.group.exam.workflow.dto.FlowInstanceDetailVO;
import com.group.exam.workflow.dto.FlowInstanceVO;
import com.group.exam.workflow.dto.FlowStartReq;
import com.group.exam.workflow.dto.FlowTaskVO;
import com.group.exam.workflow.entity.FlowBizInstance;
import com.group.exam.workflow.service.FlowInstanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    private void requireAdmin() {
        SecurityUtils.requireAnyRole(ADMIN_ROLES);
    }
}
