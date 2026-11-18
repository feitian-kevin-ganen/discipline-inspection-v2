package com.group.exam.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.workflow.dto.FlowDefinitionVO;
import com.group.exam.workflow.dto.FlowHistoryVO;
import com.group.exam.workflow.dto.FlowInstanceDetailVO;
import com.group.exam.workflow.dto.FlowInstanceVO;
import com.group.exam.workflow.dto.FlowTaskVO;
import com.group.exam.workflow.entity.FlowBizInstance;
import com.group.exam.workflow.mapper.FlowBizInstanceMapper;
import com.group.exam.workflow.service.FlowInstanceService;
import com.group.exam.workflow.spi.FlowStatusCallback;
import com.group.exam.workflow.spi.FlowUserNameResolver;
import com.group.exam.workflow.support.FlowContext;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FlowInstanceServiceImpl implements FlowInstanceService {

    private static final int MAX_LIST = 200;

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final FlowBizInstanceMapper mapper;
    /** key = Spring Bean 名称 = bizType */
    private final Map<String, FlowStatusCallback> callbacks;
    private final FlowUserNameResolver userNameResolver;

    public FlowInstanceServiceImpl(RuntimeService runtimeService,
                                   TaskService taskService,
                                   RepositoryService repositoryService,
                                   HistoryService historyService,
                                   FlowBizInstanceMapper mapper,
                                   Map<String, FlowStatusCallback> callbacks,
                                   ObjectProvider<FlowUserNameResolver> userNameResolverProvider) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.historyService = historyService;
        this.mapper = mapper;
        this.callbacks = callbacks == null ? Map.of() : callbacks;
        this.userNameResolver = userNameResolverProvider.getIfAvailable();
    }

    // ------------------------------------------------------------------ 发起

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowBizInstance start(String bizType, Long bizId, String title) {
        if (!StringUtils.hasText(bizType) || bizId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "bizType 与 bizId 不能为空");
        }
        FlowBizInstance running = findRunning(bizType, bizId);
        if (running != null) {
            return running;
        }
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(bizType)
                .latestVersion()
                .singleResult();
        if (definition == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未部署流程定义: " + bizType);
        }
        LoginUser user = SecurityUtils.getLoginUser();
        Map<String, Object> vars = new HashMap<>();
        vars.put("bizType", bizType);
        vars.put("bizId", bizId);
        vars.put("title", title);
        vars.put("startUserId", user.getUserId());
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(bizType, bizType + ":" + bizId, vars);

        LocalDateTime now = LocalDateTime.now();
        FlowBizInstance record = new FlowBizInstance();
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setProcInstId(instance.getProcessInstanceId());
        record.setProcDefKey(bizType);
        record.setTitle(title);
        record.setStartUserId(user.getUserId());
        record.setStatus(FlowBizInstance.STATUS_RUNNING);
        record.setStartTime(now);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mapper.insert(record);

        callback(bizType).ifPresent(cb ->
                cb.onStart(bizType, bizId, instance.getProcessInstanceId(), user.getUserId()));
        return record;
    }

    @Override
    public boolean hasRunning(String bizType, Long bizId) {
        return findRunning(bizType, bizId) != null;
    }

    @Override
    public FlowBizInstance findRunning(String bizType, Long bizId) {
        if (!StringUtils.hasText(bizType) || bizId == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<FlowBizInstance>()
                .eq(FlowBizInstance::getBizType, bizType)
                .eq(FlowBizInstance::getBizId, bizId)
                .eq(FlowBizInstance::getStatus, FlowBizInstance.STATUS_RUNNING)
                .orderByDesc(FlowBizInstance::getId)
                .last("LIMIT 1"));
    }

    // ------------------------------------------------------------------ 办理

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeByBiz(String bizType, Long bizId, boolean approve, String comment) {
        FlowBizInstance record = findRunning(bizType, bizId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该单据没有进行中的审批流程");
        }
        Task task = currentTask(record.getProcInstId());
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批任务不存在或已被处理");
        }
        doComplete(task, approve, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, boolean approve, String comment) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批任务不存在或已被处理");
        }
        doComplete(task, approve, comment);
    }

    private void doComplete(Task task, boolean approve, String comment) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (!approve && !StringUtils.hasText(comment)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "驳回时审批意见必填");
        }
        if (!canHandle(task, user)) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前用户无权办理该审批任务");
        }
        if (!StringUtils.hasText(task.getAssignee())) {
            taskService.setAssignee(task.getId(), String.valueOf(user.getUserId()));
        }
        String text = (approve ? "同意" : "驳回")
                + (StringUtils.hasText(comment) ? "：" + comment.trim() : "");
        taskService.addComment(task.getId(), task.getProcessInstanceId(), text);

        FlowContext.set(user.getUserId(), comment);
        try {
            if (approve) {
                taskService.complete(task.getId());
                // 监听器已做状态流转；若监听器未注册（如引擎事件被关闭）则兜底
                if (runtimeService.createProcessInstanceQuery()
                        .processInstanceId(task.getProcessInstanceId()).count() == 0) {
                    finalizeInstance(task.getProcessInstanceId(), FlowBizInstance.STATUS_APPROVED,
                            comment, user.getUserId());
                }
            } else {
                runtimeService.deleteProcessInstance(task.getProcessInstanceId(),
                        REJECT_PREFIX + (comment == null ? "" : comment.trim()));
                finalizeInstance(task.getProcessInstanceId(), FlowBizInstance.STATUS_REJECTED,
                        comment, user.getUserId());
            }
        } finally {
            FlowContext.clear();
        }
    }

    private Task currentTask(String procInstId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(procInstId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    private boolean canHandle(Task task, LoginUser user) {
        if (user.isSuperAdmin()) {
            return true;
        }
        if (StringUtils.hasText(task.getAssignee())) {
            return task.getAssignee().equals(String.valueOf(user.getUserId()));
        }
        List<String> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return taskService.createTaskQuery()
                .taskId(task.getId())
                .taskCandidateGroupIn(roles)
                .count() > 0;
    }

    // ------------------------------------------------------------------ 终止

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByBiz(String bizType, Long bizId, String comment) {
        FlowBizInstance record = findRunning(bizType, bizId);
        if (record == null) {
            return;
        }
        Long operator = currentUserIdOrNull();
        FlowContext.set(operator, comment);
        try {
            runtimeService.deleteProcessInstance(record.getProcInstId(),
                    STOP_PREFIX + (comment == null ? "" : comment.trim()));
            finalizeInstance(record.getProcInstId(), FlowBizInstance.STATUS_STOPPED, comment, operator);
        } finally {
            FlowContext.clear();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleEnded(String procInstId, boolean cancelled, String reason) {
        if (!StringUtils.hasText(procInstId)) {
            return;
        }
        if (!cancelled) {
            finalizeInstance(procInstId, FlowBizInstance.STATUS_APPROVED, FlowContext.getComment(),
                    FlowContext.getOperator());
            return;
        }
        String cause = reason == null ? "" : reason;
        String comment = cause;
        String status = FlowBizInstance.STATUS_STOPPED;
        if (cause.startsWith(REJECT_PREFIX)) {
            status = FlowBizInstance.STATUS_REJECTED;
            comment = cause.substring(REJECT_PREFIX.length());
        } else if (cause.startsWith(STOP_PREFIX)) {
            comment = cause.substring(STOP_PREFIX.length());
        }
        finalizeInstance(procInstId, status, comment, FlowContext.getOperator());
    }

    /**
     * 结束流转（幂等）：仅当记录仍为 RUNNING 时更新，并按 bizType 回调业务模块
     */
    private void finalizeInstance(String procInstId, String status, String comment, Long operatorId) {
        FlowBizInstance record = findByProcInstId(procInstId);
        if (record == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<FlowBizInstance> update = new LambdaUpdateWrapper<FlowBizInstance>()
                .eq(FlowBizInstance::getId, record.getId())
                .eq(FlowBizInstance::getStatus, FlowBizInstance.STATUS_RUNNING)
                .set(FlowBizInstance::getStatus, status)
                .set(FlowBizInstance::getEndTime, now)
                .set(FlowBizInstance::getUpdateTime, now);
        if (mapper.update(null, update) == 0) {
            return;
        }
        record.setStatus(status);
        record.setEndTime(now);

        Long operator = operatorId != null ? operatorId : FlowContext.getOperator();
        callback(record.getBizType()).ifPresent(cb -> {
            switch (status) {
                case FlowBizInstance.STATUS_APPROVED ->
                        cb.onPass(record.getBizType(), record.getBizId(), procInstId, comment, operator);
                case FlowBizInstance.STATUS_REJECTED ->
                        cb.onReject(record.getBizType(), record.getBizId(), procInstId, comment, operator);
                default ->
                        cb.onStop(record.getBizType(), record.getBizId(), procInstId, comment, operator);
            }
        });
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public List<FlowTaskVO> todoTasks() {
        LoginUser user = SecurityUtils.getLoginUser();
        Map<String, Task> collected = new LinkedHashMap<>();
        List<String> roles = user.getRoles();
        if (roles != null && !roles.isEmpty()) {
            for (Task task : taskService.createTaskQuery()
                    .taskCandidateGroupIn(roles)
                    .active()
                    .orderByTaskCreateTime()
                    .desc()
                    .list()) {
                collected.put(task.getId(), task);
            }
        }
        for (Task task : taskService.createTaskQuery()
                .taskAssignee(String.valueOf(user.getUserId()))
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list()) {
            collected.putIfAbsent(task.getId(), task);
        }
        List<Task> tasks = new ArrayList<>(collected.values());
        tasks.sort((a, b) -> compareDesc(a.getCreateTime(), b.getCreateTime()));
        if (tasks.size() > MAX_LIST) {
            tasks = tasks.subList(0, MAX_LIST);
        }
        return toTaskVOs(tasks);
    }

    @Override
    public List<FlowTaskVO> doneTasks() {
        LoginUser user = SecurityUtils.getLoginUser();
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(user.getUserId()))
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .listPage(0, MAX_LIST);
        Map<String, FlowBizInstance> records = recordsByProcInst(
                tasks.stream().map(HistoricTaskInstance::getProcessInstanceId).collect(Collectors.toSet()));
        Map<String, String> defNames = new HashMap<>();
        List<FlowTaskVO> result = new ArrayList<>(tasks.size());
        for (HistoricTaskInstance task : tasks) {
            FlowTaskVO vo = new FlowTaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setProcInstId(task.getProcessInstanceId());
            vo.setProcDefKey(task.getProcessDefinitionId() == null ? null : defKeyOf(task.getProcessDefinitionId()));
            vo.setProcDefName(defNames.computeIfAbsent(task.getProcessDefinitionId(), this::defNameOf));
            vo.setAssignee(task.getAssignee());
            vo.setCreateTime(toLocalDateTime(task.getStartTime()));
            vo.setEndTime(toLocalDateTime(task.getEndTime()));
            FlowBizInstance record = records.get(task.getProcessInstanceId());
            fillBiz(vo, record);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<FlowInstanceVO> myStarted() {
        Long userId = SecurityUtils.getUserId();
        List<FlowBizInstance> records = mapper.selectList(new LambdaQueryWrapper<FlowBizInstance>()
                .eq(FlowBizInstance::getStartUserId, userId)
                .orderByDesc(FlowBizInstance::getId)
                .last("LIMIT " + MAX_LIST));
        return toInstanceVOs(records);
    }

    @Override
    public PageResult<FlowInstanceVO> allInstances(String title, String status, long pageNum, long pageSize) {
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        LambdaQueryWrapper<FlowBizInstance> query = new LambdaQueryWrapper<FlowBizInstance>()
                .like(StringUtils.hasText(title), FlowBizInstance::getTitle, title == null ? null : title.trim())
                .eq(StringUtils.hasText(status), FlowBizInstance::getStatus, status)
                .orderByDesc(FlowBizInstance::getId);
        Page<FlowBizInstance> page = mapper.selectPage(new Page<>(current, size), query);
        return PageResult.of(page.getTotal(), toInstanceVOs(page.getRecords()), current, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopInstance(String procInstId, String comment) {
        FlowBizInstance record = findByProcInstId(procInstId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "流程实例不存在: " + procInstId);
        }
        if (!FlowBizInstance.STATUS_RUNNING.equals(record.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "流程已结束，无法终止");
        }
        Long operator = SecurityUtils.getUserId();
        FlowContext.set(operator, comment);
        try {
            runtimeService.deleteProcessInstance(procInstId, STOP_PREFIX + (comment == null ? "" : comment.trim()));
            finalizeInstance(procInstId, FlowBizInstance.STATUS_STOPPED, comment, operator);
        } finally {
            FlowContext.clear();
        }
    }

    @Override
    public Map<String, Object> diagramData(String procInstId) {
        Set<String> finished = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        Set<String> rejected = new LinkedHashSet<>();
        for (HistoricActivityInstance act : historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInstId).finished().list()) {
            finished.add(act.getActivityId());
        }
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        if (running != null) {
            active.addAll(runtimeService.getActiveActivityIds(procInstId));
        }
        // 驳回节点识别：审批意见以“驳回”开头的任务所在节点
        for (HistoricTaskInstance task : historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(procInstId).finished().list()) {
            String comment = commentOf(task.getId());
            if (comment != null && comment.startsWith("驳回")) {
                rejected.add(task.getTaskDefinitionKey());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("finished", finished);
        result.put("active", active);
        result.put("rejected", rejected);
        return result;
    }

    @Override
    public String definitionXml(String definitionId) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId)
                .singleResult();
        if (def == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        try (InputStream in = repositoryService.getResourceAsStream(def.getDeploymentId(), def.getResourceName())) {
            if (in == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "流程定义资源不存在: " + definitionId);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "读取流程定义失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeDefinitionState(String definitionId, boolean suspend) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId)
                .singleResult();
        if (def == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        if (suspend) {
            repositoryService.suspendProcessDefinitionById(definitionId, true, null);
        } else {
            repositoryService.activateProcessDefinitionById(definitionId, true, null);
        }
    }

    @Override
    public FlowInstanceDetailVO detail(String procInstId) {
        FlowBizInstance record = findByProcInstId(procInstId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "流程实例不存在: " + procInstId);
        }
        FlowInstanceDetailVO detail = new FlowInstanceDetailVO();
        detail.setInstance(toInstanceVOs(List.of(record)).get(0));
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(procInstId)
                .orderByHistoricTaskInstanceStartTime()
                .asc()
                .list();
        List<FlowHistoryVO> history = new ArrayList<>(tasks.size());
        for (HistoricTaskInstance task : tasks) {
            FlowHistoryVO item = new FlowHistoryVO();
            item.setTaskId(task.getId());
            item.setTaskName(task.getName());
            item.setAssignee(task.getAssignee());
            item.setStartTime(toLocalDateTime(task.getStartTime()));
            item.setEndTime(toLocalDateTime(task.getEndTime()));
            item.setComment(commentOf(task.getId()));
            history.add(item);
        }
        detail.setHistory(history);
        Map<String, Object> variables = new LinkedHashMap<>();
        historyService.createHistoricVariableInstanceQuery().processInstanceId(procInstId).list()
                .forEach(v -> {
                    if (v.getVariableName() != null && !v.getVariableName().startsWith("_")) {
                        variables.put(v.getVariableName(), v.getValue());
                    }
                });
        detail.setVariables(variables);
        return detail;
    }

    @Override
    public List<FlowDefinitionVO> definitions() {
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey()
                .asc()
                .list()
                .stream()
                .map(def -> {
                    FlowDefinitionVO vo = new FlowDefinitionVO();
                    vo.setId(def.getId());
                    vo.setKey(def.getKey());
                    vo.setName(def.getName());
                    vo.setVersion(def.getVersion());
                    vo.setSuspended(def.isSuspended());
                    return vo;
                })
                .toList();
    }

    // ------------------------------------------------------------------ 内部工具

    private FlowBizInstance findByProcInstId(String procInstId) {
        if (!StringUtils.hasText(procInstId)) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<FlowBizInstance>()
                .eq(FlowBizInstance::getProcInstId, procInstId)
                .orderByDesc(FlowBizInstance::getId)
                .last("LIMIT 1"));
    }

    private java.util.Optional<FlowStatusCallback> callback(String bizType) {
        return java.util.Optional.ofNullable(callbacks.get(bizType));
    }

    private Map<String, FlowBizInstance> recordsByProcInst(Collection<String> procInstIds) {
        if (procInstIds == null || procInstIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<FlowBizInstance>()
                        .in(FlowBizInstance::getProcInstId, procInstIds))
                .stream()
                .collect(Collectors.toMap(FlowBizInstance::getProcInstId, r -> r, (a, b) -> a));
    }

    private List<FlowTaskVO> toTaskVOs(List<Task> tasks) {
        Map<String, FlowBizInstance> records = recordsByProcInst(
                tasks.stream().map(Task::getProcessInstanceId).collect(Collectors.toSet()));
        Map<String, String> defNames = new HashMap<>();
        List<FlowTaskVO> result = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            FlowTaskVO vo = new FlowTaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setProcInstId(task.getProcessInstanceId());
            vo.setProcDefKey(defKeyOf(task.getProcessDefinitionId()));
            vo.setProcDefName(defNames.computeIfAbsent(task.getProcessDefinitionId(), this::defNameOf));
            vo.setAssignee(task.getAssignee());
            vo.setCreateTime(toLocalDateTime(task.getCreateTime()));
            fillBiz(vo, records.get(task.getProcessInstanceId()));
            result.add(vo);
        }
        return result;
    }

    private void fillBiz(FlowTaskVO vo, FlowBizInstance record) {
        if (record == null) {
            return;
        }
        vo.setBizType(record.getBizType());
        vo.setBizId(record.getBizId());
        vo.setBizTitle(record.getTitle());
    }

    private String defKeyOf(String processDefinitionId) {
        if (!StringUtils.hasText(processDefinitionId)) {
            return null;
        }
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        return def == null ? null : def.getKey();
    }

    private String defNameOf(String processDefinitionId) {
        if (!StringUtils.hasText(processDefinitionId)) {
            return null;
        }
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        return def == null ? null : def.getName();
    }

    private String commentOf(String taskId) {
        List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(taskId);
        if (comments == null || comments.isEmpty()) {
            return null;
        }
        return comments.stream()
                .map(org.flowable.engine.task.Comment::getFullMessage)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("；"));
    }

    private FlowInstanceVO toInstanceVO(FlowBizInstance record) {
        FlowInstanceVO vo = new FlowInstanceVO();
        vo.setId(record.getId());
        vo.setBizType(record.getBizType());
        vo.setBizId(record.getBizId());
        vo.setProcInstId(record.getProcInstId());
        vo.setProcDefKey(record.getProcDefKey());
        vo.setTitle(record.getTitle());
        vo.setStartUserId(record.getStartUserId());
        vo.setStatus(record.getStatus());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * 批量补全发起人姓名（走可选 SPI）、流程定义 id / 名称，避免逐行查询
     */
    private List<FlowInstanceVO> toInstanceVOs(List<FlowBizInstance> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = Map.of();
        if (userNameResolver != null) {
            Set<Long> userIds = records.stream()
                    .map(FlowBizInstance::getStartUserId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!userIds.isEmpty()) {
                Map<Long, String> resolved = userNameResolver.names(userIds);
                names = resolved == null ? Map.of() : resolved;
            }
        }
        Map<String, String> defNameCache = new HashMap<>();
        List<FlowInstanceVO> result = new ArrayList<>(records.size());
        for (FlowBizInstance record : records) {
            FlowInstanceVO vo = toInstanceVO(record);
            vo.setStartUserName(names.get(record.getStartUserId()));
            String definitionId = definitionIdOf(record.getProcInstId());
            vo.setDefinitionId(definitionId);
            if (definitionId != null) {
                vo.setProcDefName(defNameCache.computeIfAbsent(definitionId, this::defNameOf));
            }
            result.add(vo);
        }
        return result;
    }

    private String definitionIdOf(String procInstId) {
        if (!StringUtils.hasText(procInstId)) {
            return null;
        }
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(procInstId)
                .singleResult();
        return historic == null ? null : historic.getProcessDefinitionId();
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static int compareDesc(Date a, Date b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }
}
