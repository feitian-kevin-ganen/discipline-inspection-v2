package com.group.exam.exam.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.enums.SessionStatusEnum;
import com.group.exam.exam.service.ExamSessionService;
import com.group.exam.exam.service.SheetService;
import com.group.exam.system.service.SysLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场次自动收卷定时任务：进行中的场次到达结束时间后，
 * 自动强制收取未交答卷并将场次置为已结束，无需管理员手工干预。
 */
@Component
public class ExamAutoCloseJob {

    private static final Logger log = LoggerFactory.getLogger(ExamAutoCloseJob.class);

    private final ExamSessionService examSessionService;
    private final SheetService sheetService;
    private final SysLogService sysLogService;

    public ExamAutoCloseJob(ExamSessionService examSessionService, SheetService sheetService,
                            SysLogService sysLogService) {
        this.examSessionService = examSessionService;
        this.sheetService = sheetService;
        this.sysLogService = sysLogService;
    }

    /** 每分钟扫描一次已到结束时间的进行中场次 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void autoCloseExpiredSessions() {
        List<ExamSession> expired = examSessionService.list(new QueryWrapper<ExamSession>()
                .eq("session_status", SessionStatusEnum.IN_PROGRESS.getCode())
                .lt("end_time", LocalDateTime.now()));
        for (ExamSession session : expired) {
            try {
                closeSession(session);
            } catch (Exception e) {
                log.error("自动收卷失败, examId={}", session.getId(), e);
            }
        }
    }

    /**
     * 结束单场次：强制收卷 + 状态置为已结束。
     * 不做整体事务（自调用不生效），各服务方法自身有事务；
     * 若中途失败，场次保持进行中，下一扫描周期自动重试。
     */
    public void closeSession(ExamSession session) {
        // 二次校验状态，避免与手工结束并发重复处理
        ExamSession fresh = examSessionService.getById(session.getId());
        if (fresh == null || fresh.getSessionStatus() == null
                || fresh.getSessionStatus() != SessionStatusEnum.IN_PROGRESS.getCode()) {
            return;
        }
        int forced = sheetService.forceSubmitSheets(session.getId(), null, "到达结束时间自动收卷");
        fresh.setSessionStatus(SessionStatusEnum.ENDED.getCode());
        examSessionService.updateById(fresh);
        sysLogService.log(1, "EXAM", "场次自动结束",
                "场次[" + fresh.getSessionName() + "] 到达结束时间，自动强制收卷 " + forced + " 份并结束考试", 1);
        log.info("场次[{}] 到达结束时间自动收卷 {} 份并结束", fresh.getSessionName(), forced);
    }
}
