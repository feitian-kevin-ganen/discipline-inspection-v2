package com.group.exam.learn.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.security.LoginUser;
import com.group.exam.learn.entity.LearnMaterial;
import com.group.exam.learn.entity.LearnRecord;
import com.group.exam.learn.mapper.LearnRecordMapper;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.service.OrgUnitService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习记录服务（一人一份资料一条，uk_learn_record_um 幂等）
 */
@Service
public class LearnRecordService extends ServiceImpl<LearnRecordMapper, LearnRecord> {

    private final OrgUnitService orgUnitService;
    private final LearnMaterialService materialService;

    public LearnRecordService(OrgUnitService orgUnitService, LearnMaterialService materialService) {
        this.orgUnitService = orgUnitService;
        this.materialService = materialService;
    }

    public LearnRecord getRecord(Long userId, Long materialId) {
        return getOne(new QueryWrapper<LearnRecord>()
                .eq("user_id", userId).eq("material_id", materialId), false);
    }

    /** userId+materialId → record */
    public Map<Long, LearnRecord> mapByMaterialIds(Long userId, Collection<Long> materialIds) {
        Map<Long, LearnRecord> result = new HashMap<>();
        if (materialIds == null || materialIds.isEmpty()) {
            return result;
        }
        for (LearnRecord r : list(new QueryWrapper<LearnRecord>()
                .eq("user_id", userId).in("material_id", materialIds))) {
            result.put(r.getMaterialId(), r);
        }
        return result;
    }

    /**
     * 取学习记录，不存在则创建（快照 user/org/material 信息），首次创建时资料学习人数 +1。
     */
    public LearnRecord getOrCreate(LoginUser user, LearnMaterial material) {
        LearnRecord record = getRecord(user.getUserId(), material.getId());
        if (record != null) {
            return record;
        }
        OrgUnit unit = orgUnitService.getUnit(user.getOrgId());
        LearnRecord fresh = new LearnRecord();
        fresh.setUserId(user.getUserId());
        fresh.setUserName(user.getUserName());
        fresh.setOrgId(user.getOrgId());
        fresh.setOrgPath(user.getOrgPath());
        fresh.setOrgName(unit == null ? "" : unit.getUnitName());
        fresh.setMaterialId(material.getId());
        fresh.setMaterialTitle(material.getTitle());
        fresh.setCategoryId(material.getCategoryId());
        fresh.setProgress(BigDecimal.ZERO);
        fresh.setReadPage(0);
        fresh.setPlayPositionSec(0);
        fresh.setEffectiveDurationSec(0);
        fresh.setFinishStatus(0);
        fresh.setFirstLearnTime(LocalDateTime.now());
        fresh.setCreateBy(user.getUserId());
        try {
            save(fresh);
            // 学习人数 +1（仅首次产生记录时）
            LearnMaterial m = materialService.getById(material.getId());
            if (m != null) {
                m.setStudyCount((m.getStudyCount() == null ? 0 : m.getStudyCount()) + 1);
                materialService.updateById(m);
            }
            return fresh;
        } catch (DuplicateKeyException e) {
            // 并发首次心跳撞 uk_learn_record_um，回查已有记录
            return getRecord(user.getUserId(), material.getId());
        }
    }

    /** API 状态码：NOT_STARTED/IN_PROGRESS/COMPLETED */
    public static String statusOf(LearnRecord record) {
        if (record == null || record.getFinishStatus() == null || record.getFinishStatus() == 0) {
            return "NOT_STARTED";
        }
        return record.getFinishStatus() == 2 ? "COMPLETED" : "IN_PROGRESS";
    }

    /** 断点位置：视频取播放秒数，其余取最后阅读页码 */
    public static int lastPositionOf(LearnRecord record, Integer fileType) {
        if (record == null) {
            return 0;
        }
        if (fileType != null && fileType == LearnMaterialService.FT_VIDEO) {
            return record.getPlayPositionSec() == null ? 0 : record.getPlayPositionSec();
        }
        return record.getReadPage() == null ? 0 : record.getReadPage();
    }

    public static int effectiveSecOf(LearnRecord record) {
        return record == null || record.getEffectiveDurationSec() == null ? 0 : record.getEffectiveDurationSec();
    }

    public static BigDecimal progressOf(LearnRecord record) {
        return record == null || record.getProgress() == null ? BigDecimal.ZERO : record.getProgress();
    }
}
