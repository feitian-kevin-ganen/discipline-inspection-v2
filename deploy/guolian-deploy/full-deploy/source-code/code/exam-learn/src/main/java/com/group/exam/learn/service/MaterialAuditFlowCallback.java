package com.group.exam.learn.service;

import com.group.exam.learn.entity.LearnMaterial;
import com.group.exam.learn.mapper.LearnMaterialMapper;
import com.group.exam.workflow.spi.FlowStatusCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 资料审核流程回调：通过→已发布(2)、驳回→已驳回(4)。
 * Bean 名称即 bizType（material_audit），由 exam-workflow 按名称装配。
 */
@Component("material_audit")
public class MaterialAuditFlowCallback implements FlowStatusCallback {

    private final LearnMaterialMapper mapper;

    public MaterialAuditFlowCallback(LearnMaterialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void onPass(String bizType, Long bizId, String procInstId, String comment, Long operatorId) {
        updateStatus(bizId, 2, operatorId);
    }

    @Override
    public void onReject(String bizType, Long bizId, String procInstId, String comment, Long operatorId) {
        updateStatus(bizId, 4, operatorId);
    }

    private void updateStatus(Long materialId, int status, Long operatorId) {
        if (materialId == null) {
            return;
        }
        LearnMaterial m = new LearnMaterial();
        m.setId(materialId);
        m.setAuditStatus(status);
        m.setAuditBy(operatorId);
        m.setAuditTime(LocalDateTime.now());
        m.setUpdateBy(operatorId);
        mapper.updateById(m);
    }
}
