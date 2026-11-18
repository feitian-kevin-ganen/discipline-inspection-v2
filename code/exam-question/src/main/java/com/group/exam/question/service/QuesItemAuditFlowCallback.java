package com.group.exam.question.service;

import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.ItemStatusEnum;
import com.group.exam.question.mapper.QuesItemMapper;
import com.group.exam.workflow.spi.FlowStatusCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 题目审核流程回调：流程结束/驳回时把结果写回题目状态。
 * Bean 名称即 bizType（ques_item_audit），由 exam-workflow 按名称装配。
 * 直接用 Mapper 更新，避免与 QuesItemService 形成循环依赖。
 */
@Component("ques_item_audit")
public class QuesItemAuditFlowCallback implements FlowStatusCallback {

    private final QuesItemMapper mapper;

    public QuesItemAuditFlowCallback(QuesItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void onPass(String bizType, Long bizId, String procInstId, String comment, Long operatorId) {
        updateStatus(bizId, ItemStatusEnum.ENABLED.getCode(), operatorId);
    }

    @Override
    public void onReject(String bizType, Long bizId, String procInstId, String comment, Long operatorId) {
        updateStatus(bizId, ItemStatusEnum.DRAFT.getCode(), operatorId);
    }

    private void updateStatus(Long itemId, int status, Long operatorId) {
        if (itemId == null) {
            return;
        }
        QuesItem item = new QuesItem();
        item.setId(itemId);
        item.setStatus(status);
        item.setAuditBy(operatorId);
        item.setAuditTime(LocalDateTime.now());
        item.setUpdateBy(operatorId);
        mapper.updateById(item);
    }
}
