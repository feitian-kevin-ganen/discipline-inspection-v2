package com.group.exam.learn.service;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.learn.dto.HeartbeatRequest;
import com.group.exam.learn.entity.LearnMaterial;
import com.group.exam.learn.entity.LearnRecord;
import com.group.exam.system.service.SysConfigService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学习心跳服务（LEARN-04，FR-LEARN-03/05）。
 * 规则（按任务口径）：
 * - eventId Redis 去重（learn:hb:idem:{eventId}，24h），重复上报直接返回当前进度；
 * - 一人一份资料一条 t_learn_record，不存在则创建快照；
 * - progress/readPage/playPositionSec 单调只增，乱序/重复上报不后退；
 * - effective_duration_sec 累加客户端上报增量，单次增量封顶 learn.heartbeat.timeout(默认60)秒，防挂机刷时长；
 * - 完成判定：progress≥100 或（有标称 duration_sec 且 effective/duration ≥ learn.completeThreshold(默认0.9)）
 *   → finish_status=2 + finish_time，并回刷涉及本人的 t_learn_task_user。
 */
@Service
public class HeartBeatService {

    private static final String IDEM_KEY_PREFIX = "learn:hb:idem:";

    private final LearnMaterialService materialService;
    private final LearnRecordService recordService;
    private final LearnScopeChecker scopeChecker;
    private final LearnTaskService taskService;
    private final SysConfigService sysConfigService;
    private final StringRedisTemplate redisTemplate;

    public HeartBeatService(LearnMaterialService materialService, LearnRecordService recordService,
                            LearnScopeChecker scopeChecker, LearnTaskService taskService,
                            SysConfigService sysConfigService, StringRedisTemplate redisTemplate) {
        this.materialService = materialService;
        this.recordService = recordService;
        this.scopeChecker = scopeChecker;
        this.taskService = taskService;
        this.sysConfigService = sysConfigService;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> heartbeat(HeartbeatRequest req) {
        LoginUser user = SecurityUtils.getLoginUser();
        LearnMaterial m = materialService.requirePublished(req.getMaterialId());
        if (!scopeChecker.visible(m.getVisibleScope(), user.getOrgPath())) {
            throw new BizException(ErrorCode.MATERIAL_NOT_VISIBLE);
        }

        // 幂等去重：重复 eventId 直接返回当前进度（code=0）
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(IDEM_KEY_PREFIX + req.getEventId(), "1", Duration.ofHours(24));
        LearnRecord record = recordService.getOrCreate(user, m);
        if (Boolean.FALSE.equals(first)) {
            return resp(record, m);
        }

        // 有效时长累加：单次增量封顶 learn.heartbeat.timeout(默认60)秒
        int timeout = sysConfigService.getInt("learn.heartbeat.timeout", 60);
        int delta = req.getDeltaSec() == null ? 0 : Math.max(0, Math.min(req.getDeltaSec(), timeout));
        record.setEffectiveDurationSec(LearnRecordService.effectiveSecOf(record) + delta);

        // 断点锚点单调只增
        if (req.getPositionSec() != null) {
            record.setPlayPositionSec(Math.max(
                    record.getPlayPositionSec() == null ? 0 : record.getPlayPositionSec(), req.getPositionSec()));
        }
        if (req.getPageNo() != null) {
            record.setReadPage(Math.max(
                    record.getReadPage() == null ? 0 : record.getReadPage(), req.getPageNo()));
        }

        // 进度单调推进：视频按最大播放位置/标称时长，文档按最后页码/页数
        BigDecimal computed = computeProgress(m, record);
        BigDecimal current = LearnRecordService.progressOf(record);
        if (computed.compareTo(current) > 0) {
            record.setProgress(computed);
        }

        LocalDateTime now = LocalDateTime.now();
        if (record.getFinishStatus() == null || record.getFinishStatus() == 0) {
            record.setFinishStatus(1);
        }
        if (record.getFirstLearnTime() == null) {
            record.setFirstLearnTime(now);
        }
        record.setLastHeartbeatTime(now);
        record.setLastLearnTime(now);
        record.setUpdateBy(user.getUserId());

        // 完成判定：进度满 或 有效时长达标称时长阈值
        boolean justFinished = false;
        if (record.getFinishStatus() != 2) {
            boolean progressDone = record.getProgress().compareTo(BigDecimal.valueOf(100)) >= 0;
            double threshold = sysConfigService.getDouble("learn.completeThreshold", 0.9);
            boolean durationDone = m.getDurationSec() != null && m.getDurationSec() > 0
                    && record.getEffectiveDurationSec() >= m.getDurationSec() * threshold;
            if (progressDone || durationDone) {
                record.setFinishStatus(2);
                record.setFinishTime(now);
                justFinished = true;
            }
        }
        recordService.updateById(record);

        if (justFinished) {
            taskService.refreshOnMaterialFinish(user.getUserId(), m.getId());
        }
        return resp(record, m);
    }

    private BigDecimal computeProgress(LearnMaterial m, LearnRecord record) {
        BigDecimal computed = BigDecimal.ZERO;
        if (m.getFileType() != null && m.getFileType() == LearnMaterialService.FT_VIDEO) {
            if (m.getDurationSec() != null && m.getDurationSec() > 0) {
                int pos = Math.min(record.getPlayPositionSec(), m.getDurationSec());
                computed = BigDecimal.valueOf(pos * 100.0 / m.getDurationSec());
            }
        } else if (m.getPageCount() != null && m.getPageCount() > 0) {
            int page = Math.min(record.getReadPage(), m.getPageCount());
            computed = BigDecimal.valueOf(page * 100.0 / m.getPageCount());
        }
        return computed.setScale(2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
    }

    private Map<String, Object> resp(LearnRecord record, LearnMaterial m) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("progressPct", LearnRecordService.progressOf(record));
        data.put("effectiveSec", LearnRecordService.effectiveSecOf(record));
        data.put("status", LearnRecordService.statusOf(record));
        data.put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return data;
    }
}
