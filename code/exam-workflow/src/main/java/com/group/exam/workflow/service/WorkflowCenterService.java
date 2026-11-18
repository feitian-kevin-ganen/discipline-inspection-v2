package com.group.exam.workflow.service;

import com.group.exam.common.core.PageResult;
import com.group.exam.workflow.dto.FlowCopyVO;
import com.group.exam.workflow.dto.FlowDefinitionVO;
import com.group.exam.workflow.dto.FlowTimeoutVO;
import com.group.exam.workflow.entity.FlowDblink;
import com.group.exam.workflow.entity.FlowFormDef;
import com.group.exam.workflow.entity.FlowMessageRegistry;
import com.group.exam.workflow.entity.FlowTaskExt;

import java.util.List;

/**
 * 流程中心管理能力：抄送、表单模板、外部数据源、消息注册、节点超时与催办、流程设计器。
 */
public interface WorkflowCenterService {

    // ---------------------------------------------------------------- 抄送

    PageResult<FlowCopyVO> myCopies(String readFlag, long pageNum, long pageSize);

    long unreadCopyCount();

    void markCopyRead(Long id);

    int markAllCopyRead();

    /** 办理审批时写入抄送（自动过滤发起人自己、去重） */
    void addCopies(String procInstId, String taskDefKey, String taskName, String title,
                   Long fromUserId, List<Long> toUserIds);

    // ---------------------------------------------------------------- 表单

    List<FlowFormDef> listForms(String name, String procDefKey);

    FlowFormDef saveForm(FlowFormDef form);

    void deleteForm(Long id);

    void changeFormStatus(Long id, String status);

    // ---------------------------------------------------------------- 数据源

    List<FlowDblink> listDblinks(String name);

    FlowDblink saveDblink(FlowDblink dblink);

    void deleteDblink(Long id);

    /** 按已保存配置或表单传入配置测试连接，返回数据库产品与版本 */
    String testDblink(FlowDblink dblink);

    // ---------------------------------------------------------------- 消息注册

    List<FlowMessageRegistry> listMessages();

    FlowMessageRegistry saveMessage(FlowMessageRegistry registry);

    void deleteMessage(Long id);

    /** 触发等待中的消息事件，返回推进的实例数 */
    int triggerMessage(String messageRef, String procInstId);

    // ---------------------------------------------------------------- 超时与催办

    List<FlowTaskExt> listTaskExts(String procDefId);

    FlowTaskExt saveTaskExt(FlowTaskExt ext);

    void deleteTaskExt(Long id);

    PageResult<FlowTimeoutVO> timeoutTasks(long pageNum, long pageSize);

    /** 记录一次催办，返回提示文案 */
    String urge(String taskId);

    // ---------------------------------------------------------------- 设计器

    List<FlowDefinitionVO> designerList();

    String definitionXmlByKey(String key);

    String deploy(String name, String xml);
}
