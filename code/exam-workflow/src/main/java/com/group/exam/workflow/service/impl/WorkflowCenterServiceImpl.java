package com.group.exam.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.workflow.dto.FlowCopyVO;
import com.group.exam.workflow.dto.FlowDefinitionVO;
import com.group.exam.workflow.dto.FlowTimeoutVO;
import com.group.exam.workflow.entity.FlowBizInstance;
import com.group.exam.workflow.entity.FlowCopy;
import com.group.exam.workflow.entity.FlowDblink;
import com.group.exam.workflow.entity.FlowFormDef;
import com.group.exam.workflow.entity.FlowMessageRegistry;
import com.group.exam.workflow.entity.FlowNotifyLog;
import com.group.exam.workflow.entity.FlowTaskExt;
import com.group.exam.workflow.mapper.FlowBizInstanceMapper;
import com.group.exam.workflow.mapper.FlowCopyMapper;
import com.group.exam.workflow.mapper.FlowDblinkMapper;
import com.group.exam.workflow.mapper.FlowFormDefMapper;
import com.group.exam.workflow.mapper.FlowMessageRegistryMapper;
import com.group.exam.workflow.mapper.FlowNotifyLogMapper;
import com.group.exam.workflow.mapper.FlowTaskExtMapper;
import com.group.exam.workflow.service.WorkflowCenterService;
import com.group.exam.workflow.spi.FlowUserNameResolver;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WorkflowCenterServiceImpl implements WorkflowCenterService {

    private static final int MAX_LIST = 200;

    private final FlowCopyMapper copyMapper;
    private final FlowFormDefMapper formMapper;
    private final FlowDblinkMapper dblinkMapper;
    private final FlowMessageRegistryMapper messageMapper;
    private final FlowTaskExtMapper taskExtMapper;
    private final FlowNotifyLogMapper notifyLogMapper;
    private final FlowBizInstanceMapper bizMapper;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final FlowUserNameResolver userNameResolver;

    public WorkflowCenterServiceImpl(FlowCopyMapper copyMapper,
                                     FlowFormDefMapper formMapper,
                                     FlowDblinkMapper dblinkMapper,
                                     FlowMessageRegistryMapper messageMapper,
                                     FlowTaskExtMapper taskExtMapper,
                                     FlowNotifyLogMapper notifyLogMapper,
                                     FlowBizInstanceMapper bizMapper,
                                     RuntimeService runtimeService,
                                     TaskService taskService,
                                     RepositoryService repositoryService,
                                     ObjectProvider<FlowUserNameResolver> userNameResolverProvider) {
        this.copyMapper = copyMapper;
        this.formMapper = formMapper;
        this.dblinkMapper = dblinkMapper;
        this.messageMapper = messageMapper;
        this.taskExtMapper = taskExtMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.bizMapper = bizMapper;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.userNameResolver = userNameResolverProvider.getIfAvailable();
    }

    // ---------------------------------------------------------------- 抄送

    @Override
    public PageResult<FlowCopyVO> myCopies(String readFlag, long pageNum, long pageSize) {
        Long userId = SecurityUtils.getUserId();
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        Page<FlowCopy> page = copyMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<FlowCopy>()
                        .eq(FlowCopy::getToUserId, userId)
                        .eq(StringUtils.hasText(readFlag), FlowCopy::getReadFlag, readFlag)
                        .orderByDesc(FlowCopy::getId));
        List<FlowCopy> records = page.getRecords();
        Set<Long> userIds = records.stream().map(FlowCopy::getFromUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = resolveNames(userIds);
        List<FlowCopyVO> list = records.stream().map(c -> {
            FlowCopyVO vo = new FlowCopyVO();
            vo.setId(c.getId());
            vo.setProcInstId(c.getProcInstId());
            vo.setTaskDefKey(c.getTaskDefKey());
            vo.setTaskName(c.getTaskName());
            vo.setTitle(c.getTitle());
            vo.setFromUserId(c.getFromUserId());
            vo.setFromUserName(names.get(c.getFromUserId()));
            vo.setToUserId(c.getToUserId());
            vo.setReadFlag(c.getReadFlag());
            vo.setCreateTime(c.getCreateTime());
            vo.setReadTime(c.getReadTime());
            return vo;
        }).toList();
        return PageResult.of(page.getTotal(), list, current, size);
    }

    @Override
    public long unreadCopyCount() {
        return copyMapper.selectCount(new LambdaQueryWrapper<FlowCopy>()
                .eq(FlowCopy::getToUserId, SecurityUtils.getUserId())
                .eq(FlowCopy::getReadFlag, "0"));
    }

    @Override
    public void markCopyRead(Long id) {
        FlowCopy copy = copyMapper.selectById(id);
        if (copy == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "抄送记录不存在");
        }
        if (!Objects.equals(copy.getToUserId(), SecurityUtils.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己的抄送记录");
        }
        if ("1".equals(copy.getReadFlag())) {
            return;
        }
        copy.setReadFlag("1");
        copy.setReadTime(LocalDateTime.now());
        copyMapper.updateById(copy);
    }

    @Override
    public int markAllCopyRead() {
        List<FlowCopy> unread = copyMapper.selectList(new LambdaQueryWrapper<FlowCopy>()
                .eq(FlowCopy::getToUserId, SecurityUtils.getUserId())
                .eq(FlowCopy::getReadFlag, "0"));
        LocalDateTime now = LocalDateTime.now();
        for (FlowCopy copy : unread) {
            copy.setReadFlag("1");
            copy.setReadTime(now);
            copyMapper.updateById(copy);
        }
        return unread.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCopies(String procInstId, String taskDefKey, String taskName, String title,
                          Long fromUserId, List<Long> toUserIds) {
        if (procInstId == null || toUserIds == null || toUserIds.isEmpty()) {
            return;
        }
        Set<Long> targets = new LinkedHashSet<>();
        for (Long id : toUserIds) {
            if (id != null && !id.equals(fromUserId)) {
                targets.add(id);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long target : targets) {
            FlowCopy copy = new FlowCopy();
            copy.setProcInstId(procInstId);
            copy.setTaskDefKey(taskDefKey);
            copy.setTaskName(taskName);
            copy.setTitle(title);
            copy.setFromUserId(fromUserId);
            copy.setToUserId(target);
            copy.setReadFlag("0");
            copy.setCreateTime(now);
            copyMapper.insert(copy);
        }
    }

    // ---------------------------------------------------------------- 表单

    @Override
    public List<FlowFormDef> listForms(String name, String procDefKey) {
        return formMapper.selectList(new LambdaQueryWrapper<FlowFormDef>()
                .like(StringUtils.hasText(name), FlowFormDef::getName, name)
                .eq(StringUtils.hasText(procDefKey), FlowFormDef::getProcDefKey, procDefKey)
                .orderByDesc(FlowFormDef::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowFormDef saveForm(FlowFormDef form) {
        if (form == null || !StringUtils.hasText(form.getFormKey()) || !StringUtils.hasText(form.getName())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "表单 Key 与名称不能为空");
        }
        form.setFormKey(form.getFormKey().trim());
        form.setName(form.getName().trim());
        LocalDateTime now = LocalDateTime.now();
        if (form.getId() == null) {
            Long exists = formMapper.selectCount(new LambdaQueryWrapper<FlowFormDef>()
                    .eq(FlowFormDef::getFormKey, form.getFormKey()));
            if (exists != null && exists > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "表单 Key 已存在: " + form.getFormKey());
            }
            form.setVersion(1);
            form.setStatus(StringUtils.hasText(form.getStatus()) ? form.getStatus() : "0");
            form.setCreateBy(SecurityUtils.getLoginUser().getUserName());
            form.setCreateTime(now);
            form.setUpdateTime(now);
            formMapper.insert(form);
        } else {
            FlowFormDef old = formMapper.selectById(form.getId());
            if (old == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "表单不存在");
            }
            Long dup = formMapper.selectCount(new LambdaQueryWrapper<FlowFormDef>()
                    .eq(FlowFormDef::getFormKey, form.getFormKey())
                    .ne(FlowFormDef::getId, form.getId()));
            if (dup != null && dup > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "表单 Key 已存在: " + form.getFormKey());
            }
            form.setVersion(old.getVersion() == null ? 1 : old.getVersion() + 1);
            form.setCreateBy(old.getCreateBy());
            form.setCreateTime(old.getCreateTime());
            form.setUpdateTime(now);
            formMapper.updateById(form);
        }
        return form;
    }

    @Override
    public void deleteForm(Long id) {
        formMapper.deleteById(id);
    }

    @Override
    public void changeFormStatus(Long id, String status) {
        FlowFormDef form = formMapper.selectById(id);
        if (form == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "表单不存在");
        }
        form.setStatus(status);
        form.setUpdateTime(LocalDateTime.now());
        formMapper.updateById(form);
    }

    // ---------------------------------------------------------------- 数据源

    @Override
    public List<FlowDblink> listDblinks(String name) {
        return dblinkMapper.selectList(new LambdaQueryWrapper<FlowDblink>()
                .like(StringUtils.hasText(name), FlowDblink::getName, name)
                .orderByDesc(FlowDblink::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowDblink saveDblink(FlowDblink dblink) {
        if (dblink == null || !StringUtils.hasText(dblink.getName())
                || !StringUtils.hasText(dblink.getJdbcUrl())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "数据源名称与 JDBC 地址不能为空");
        }
        if (dblink.getId() == null) {
            dblink.setStatus(StringUtils.hasText(dblink.getStatus()) ? dblink.getStatus() : "0");
            dblink.setCreateTime(LocalDateTime.now());
            dblinkMapper.insert(dblink);
        } else {
            FlowDblink old = dblinkMapper.selectById(dblink.getId());
            if (old == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "数据源不存在");
            }
            if (!StringUtils.hasText(dblink.getPassword())) {
                dblink.setPassword(old.getPassword());
            }
            dblink.setCreateTime(old.getCreateTime());
            dblinkMapper.updateById(dblink);
        }
        return dblink;
    }

    @Override
    public void deleteDblink(Long id) {
        dblinkMapper.deleteById(id);
    }

    @Override
    public String testDblink(FlowDblink dblink) {
        FlowDblink target = dblink;
        if (target != null && target.getId() != null && !StringUtils.hasText(target.getJdbcUrl())) {
            target = dblinkMapper.selectById(target.getId());
        }
        if (target == null || !StringUtils.hasText(target.getJdbcUrl())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请先填写 JDBC 地址");
        }
        DriverManager.setLoginTimeout(5);
        try (Connection conn = DriverManager.getConnection(target.getJdbcUrl(),
                target.getUsername(), target.getPassword() == null ? "" : target.getPassword())) {
            DatabaseMetaData meta = conn.getMetaData();
            return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "连接失败: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 消息注册

    @Override
    public List<FlowMessageRegistry> listMessages() {
        return messageMapper.selectList(new LambdaQueryWrapper<FlowMessageRegistry>()
                .orderByDesc(FlowMessageRegistry::getId));
    }

    @Override
    public FlowMessageRegistry saveMessage(FlowMessageRegistry registry) {
        if (registry == null || !StringUtils.hasText(registry.getMessageRef())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "消息引用（messageRef）不能为空");
        }
        if (registry.getId() == null) {
            Long exists = messageMapper.selectCount(new LambdaQueryWrapper<FlowMessageRegistry>()
                    .eq(FlowMessageRegistry::getMessageRef, registry.getMessageRef().trim()));
            if (exists != null && exists > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "消息引用已存在: " + registry.getMessageRef());
            }
            registry.setMessageRef(registry.getMessageRef().trim());
            registry.setCreateTime(LocalDateTime.now());
            messageMapper.insert(registry);
        } else {
            FlowMessageRegistry old = messageMapper.selectById(registry.getId());
            if (old == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "消息注册不存在");
            }
            registry.setCreateTime(old.getCreateTime());
            messageMapper.updateById(registry);
        }
        return registry;
    }

    @Override
    public void deleteMessage(Long id) {
        messageMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int triggerMessage(String messageRef, String procInstId) {
        if (!StringUtils.hasText(messageRef)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "消息引用不能为空");
        }
        var query = runtimeService.createExecutionQuery().messageEventSubscriptionName(messageRef);
        if (StringUtils.hasText(procInstId)) {
            query = query.processInstanceId(procInstId);
        }
        List<Execution> executions = query.list();
        int count = 0;
        for (Execution execution : executions) {
            runtimeService.messageEventReceived(messageRef, execution.getId());
            count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- 超时与催办

    @Override
    public List<FlowTaskExt> listTaskExts(String procDefId) {
        return taskExtMapper.selectList(new LambdaQueryWrapper<FlowTaskExt>()
                .eq(StringUtils.hasText(procDefId), FlowTaskExt::getProcDefId, procDefId)
                .orderByDesc(FlowTaskExt::getId));
    }

    @Override
    public FlowTaskExt saveTaskExt(FlowTaskExt ext) {
        if (ext == null || !StringUtils.hasText(ext.getProcDefId()) || !StringUtils.hasText(ext.getTaskDefKey())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "流程定义与节点 Key 不能为空");
        }
        if (ext.getTimeoutHours() == null || ext.getTimeoutHours() <= 0) {
            ext.setTimeoutHours(24);
        }
        if (ext.getId() == null) {
            Long exists = taskExtMapper.selectCount(new LambdaQueryWrapper<FlowTaskExt>()
                    .eq(FlowTaskExt::getProcDefId, ext.getProcDefId())
                    .eq(FlowTaskExt::getTaskDefKey, ext.getTaskDefKey()));
            if (exists != null && exists > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "该流程定义的节点超时配置已存在");
            }
            ext.setCreateTime(LocalDateTime.now());
            taskExtMapper.insert(ext);
        } else {
            FlowTaskExt old = taskExtMapper.selectById(ext.getId());
            if (old == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "超时配置不存在");
            }
            ext.setCreateTime(old.getCreateTime());
            taskExtMapper.updateById(ext);
        }
        return ext;
    }

    @Override
    public void deleteTaskExt(Long id) {
        taskExtMapper.deleteById(id);
    }

    @Override
    public PageResult<FlowTimeoutVO> timeoutTasks(long pageNum, long pageSize) {
        List<FlowTaskExt> configs = taskExtMapper.selectList(new LambdaQueryWrapper<FlowTaskExt>());
        Map<String, FlowTaskExt> byTask = new LinkedHashMap<>();
        for (FlowTaskExt ext : configs) {
            ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(ext.getProcDefId()).singleResult();
            if (def == null) {
                continue;
            }
            for (Task task : taskService.createTaskQuery()
                    .processDefinitionKey(def.getKey())
                    .taskDefinitionKey(ext.getTaskDefKey())
                    .active()
                    .list()) {
                FlowTaskExt exists = byTask.get(task.getId());
                if (exists == null
                        || (!Objects.equals(exists.getProcDefId(), task.getProcessDefinitionId())
                        && Objects.equals(ext.getProcDefId(), task.getProcessDefinitionId()))) {
                    byTask.put(task.getId(), ext);
                }
            }
        }
        LocalDateTime now = LocalDateTime.now();
        List<FlowTimeoutVO> all = new ArrayList<>();
        Map<String, String> titles = titlesByProcInst(byTask.keySet().stream()
                .map(id -> taskService.createTaskQuery().taskId(id).singleResult())
                .filter(Objects::nonNull)
                .map(Task::getProcessInstanceId)
                .collect(Collectors.toSet()));
        Set<Long> assignees = new LinkedHashSet<>();
        for (String taskId : byTask.keySet()) {
            Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (task == null) {
                continue;
            }
            FlowTaskExt ext = byTask.get(taskId);
            LocalDateTime created = task.getCreateTime() == null ? null
                    : LocalDateTime.ofInstant(task.getCreateTime().toInstant(), ZoneId.systemDefault());
            if (created == null) {
                continue;
            }
            long hours = Duration.between(created, now).toHours();
            if (hours < ext.getTimeoutHours()) {
                continue;
            }
            FlowTimeoutVO vo = new FlowTimeoutVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setProcInstId(task.getProcessInstanceId());
            vo.setTitle(titles.get(task.getProcessInstanceId()));
            vo.setAssignee(task.getAssignee());
            vo.setCreateTime(created);
            vo.setTimeoutHours(ext.getTimeoutHours());
            vo.setOverHours(hours - ext.getTimeoutHours());
            if (StringUtils.hasText(task.getAssignee())) {
                try {
                    assignees.add(Long.parseLong(task.getAssignee()));
                } catch (NumberFormatException ignored) {
                    // 非数字办理人不解析姓名
                }
            }
            all.add(vo);
        }
        Map<Long, String> names = resolveNames(assignees);
        all.forEach(vo -> {
            if (StringUtils.hasText(vo.getAssignee())) {
                try {
                    vo.setAssigneeName(names.get(Long.parseLong(vo.getAssignee())));
                } catch (NumberFormatException ignored) {
                    // 保留空姓名
                }
            }
        });
        all.sort((a, b) -> b.getOverHours().compareTo(a.getOverHours()));
        int from = (int) Math.min((Math.max(pageNum, 1) - 1) * Math.max(pageSize, 1), all.size());
        int to = (int) Math.min(from + Math.max(pageSize, 1), all.size());
        return PageResult.of(all.size(), all.subList(from, to), Math.max(pageNum, 1), Math.max(pageSize, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String urge(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在或已办理");
        }
        LoginUser operator = SecurityUtils.getLoginUser();
        FlowNotifyLog log = new FlowNotifyLog();
        log.setProcInstId(task.getProcessInstanceId());
        log.setTaskId(taskId);
        log.setNotifyType("URGE");
        log.setChannel("INNER");
        if (StringUtils.hasText(task.getAssignee())) {
            try {
                log.setReceiverId(Long.parseLong(task.getAssignee()));
            } catch (NumberFormatException ignored) {
                // 非数字办理人不记录接收人 ID
            }
        }
        log.setContent("任务【" + task.getName() + "】已超时，请尽快办理（催办人：" + operator.getUserName() + "）");
        log.setSendStatus("SUCCESS");
        log.setCreateTime(LocalDateTime.now());
        notifyLogMapper.insert(log);
        return "已记录催办：" + log.getContent();
    }

    // ---------------------------------------------------------------- 设计器

    @Override
    public List<FlowDefinitionVO> designerList() {
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

    @Override
    public String definitionXmlByKey(String key) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(key)
                .latestVersion()
                .singleResult();
        if (def == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "流程定义不存在: " + key);
        }
        try (InputStream in = repositoryService.getResourceAsStream(def.getDeploymentId(), def.getResourceName())) {
            if (in == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "流程定义资源不存在: " + key);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "读取流程定义失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deploy(String name, String xml) {
        if (!StringUtils.hasText(xml)
                || !xml.contains("<definitions")
                || !xml.contains("<process")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "BPMN XML 不合法：缺少 definitions/process 节点");
        }
        String deployName = StringUtils.hasText(name) ? name.trim() : "process";
        String resourceName = deployName.endsWith(".bpmn20.xml") ? deployName : deployName + ".bpmn20.xml";
        try {
            return repositoryService.createDeployment()
                    .name(deployName)
                    .addString(resourceName, xml)
                    .deploy()
                    .getId();
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "部署失败: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 内部工具

    private Map<Long, String> resolveNames(Collection<Long> userIds) {
        if (userNameResolver == null || userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> resolved = userNameResolver.names(userIds);
        return resolved == null ? Map.of() : resolved;
    }

    private Map<String, String> titlesByProcInst(Collection<String> procInstIds) {
        if (procInstIds == null || procInstIds.isEmpty()) {
            return Map.of();
        }
        return bizMapper.selectList(new LambdaQueryWrapper<FlowBizInstance>()
                        .in(FlowBizInstance::getProcInstId, procInstIds))
                .stream()
                .collect(Collectors.toMap(FlowBizInstance::getProcInstId, FlowBizInstance::getTitle, (a, b) -> a));
    }
}
