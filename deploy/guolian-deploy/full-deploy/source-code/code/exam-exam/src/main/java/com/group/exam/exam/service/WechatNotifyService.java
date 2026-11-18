package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.SubscribeReportReq;
import com.group.exam.exam.dto.WechatBindReq;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.entity.NotifyWxLog;
import com.group.exam.exam.mapper.ExamSessionMapper;
import com.group.exam.exam.mapper.NotifyWxLogMapper;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.OrgUserService;
import com.group.exam.system.service.SysConfigService;
import com.group.exam.system.service.SysLogService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 微信订阅通知（NOTIFY-WX-01~04，V1.1 新增，骨架实现）
 *
 * <p>订阅额度存 Redis（wx:sub:{userId} hash，field 为场景编码，value 为剩余次数，不落库），
 * 每次成功下发扣减一次；推送留痕见 t_notify_wx_log（只增不改）。
 * 真实微信出站调用（code2session / subscribeMessage.send）需配置 wx.mp.appid/secret 后实现，
 * 当前为占位（配置值为 {@code <WX_APPID>} 等占位符时走开发模式）。
 */
@Service
public class WechatNotifyService extends ServiceImpl<NotifyWxLogMapper, NotifyWxLog> {

    /** 绑定接口限流 key 前缀：10 次/分钟/用户（10006） */
    private static final String RATE_LIMIT_PREFIX = "exam:ratelimit:wxbind:";
    private static final int RATE_LIMIT_MAX = 10;
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60;

    /** 订阅额度 hash：wx:sub:{userId}，field=场景编码，value=剩余次数 */
    private static final String SUB_HASH_PREFIX = "wx:sub:";
    /** accept 幂等标记：wx:sub:accept:{userId}:{scene}，重复上报不累加 */
    private static final String SUB_ACCEPT_PREFIX = "wx:sub:accept:";

    /** 场景编码 → t_notify_wx_log.scene / 模板配置键 */
    private static final Map<String, SceneDef> SCENES = new LinkedHashMap<>();

    static {
        SCENES.put("EXAM_PUBLISH", new SceneDef(1, "wx.tpl.examPublish"));
        SCENES.put("EXAM_REMIND_BEFORE", new SceneDef(2, "wx.tpl.examRemind"));
        SCENES.put("EXAM_REMIND_DEADLINE", new SceneDef(3, "wx.tpl.examRemind"));
        SCENES.put("SCORE_PUBLISHED", new SceneDef(4, "wx.tpl.scorePublish"));
    }

    private record SceneDef(int code, String tplConfigKey) {
    }

    /** 发送状态编码 ↔ API */
    private static final Map<Integer, String> SEND_STATUS_API = Map.of(
            0, "PENDING", 1, "SUCCESS", 2, "FAILED", 3, "GIVE_UP");

    private final OrgUserService orgUserService;
    private final OrgUnitService orgUnitService;
    private final ExamSessionMapper examSessionMapper;
    private final SysConfigService sysConfigService;
    private final SysLogService sysLogService;
    private final StringRedisTemplate redisTemplate;

    public WechatNotifyService(OrgUserService orgUserService, OrgUnitService orgUnitService,
                               ExamSessionMapper examSessionMapper, SysConfigService sysConfigService,
                               SysLogService sysLogService, StringRedisTemplate redisTemplate) {
        this.orgUserService = orgUserService;
        this.orgUnitService = orgUnitService;
        this.examSessionMapper = examSessionMapper;
        this.sysConfigService = sysConfigService;
        this.sysLogService = sysLogService;
        this.redisTemplate = redisTemplate;
    }

    // ---------------------------------------------------------------- NOTIFY-WX-01 微信绑定

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bind(WechatBindReq req) {
        Long userId = SecurityUtils.getUserId();
        checkRateLimit(userId);
        OrgUser user = orgUserService.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        if (req != null && Boolean.TRUE.equals(req.getUnbind())) {
            // 解绑：mp_openid/unionid 置空（updateById 忽略 null 字段，须用 UpdateWrapper 显式置空）
            orgUserService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OrgUser>()
                    .eq("id", userId).set("mp_openid", null).set("unionid", null));
            sysLogService.log(2, "USER", "微信解绑", "用户[" + user.getUserName() + "(" + user.getJobNo() + ")] 解绑微信", 1);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("openidMasked", null);
            result.put("alreadyBound", false);
            return result;
        }

        String appid = sysConfigService.getValue("wx.mp.appid");
        String secret = sysConfigService.getValue("wx.mp.secret");
        boolean devMode = isPlaceholder(appid) || isPlaceholder(secret);
        String openid;
        if (devMode) {
            // 开发模式（微信凭据为占位符）：直接接受 body 里的 openid 写入当前用户
            openid = req == null ? null : req.getOpenid();
            if (!StringUtils.hasText(openid)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "开发模式须传 openid（微信凭据未配置）");
            }
        } else {
            // TODO 真实环境：以 code 调用微信 code2session（appid+secret+code 换 openid/unionid），
            //  出站调用留待微信凭据配置后实现；当前无凭据，暂以 body.openid 透传占位（联调用）。
            openid = req == null ? null : req.getOpenid();
            if (!StringUtils.hasText(openid)) {
                if (req == null || !StringUtils.hasText(req.getCode())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "code 不能为空");
                }
                throw new BizException(ErrorCode.WX_CODE_INVALID, "code2session 出站调用未实现（微信凭据待配置）");
            }
        }
        openid = openid.trim();

        // openid 一账号一绑定：已被其他账号绑定时先解绑旧账号（uk_org_user_mpopenid 部分唯一索引兜底）
        OrgUser other = orgUserService.getOne(new QueryWrapper<OrgUser>()
                .eq("mp_openid", openid), false);
        if (other != null && !other.getId().equals(userId)) {
            orgUserService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OrgUser>()
                    .eq("id", other.getId()).set("mp_openid", null).set("unionid", null));
            sysLogService.log(2, "USER", "微信绑定冲突解绑",
                    "openid[" + maskOpenid(openid) + "] 原绑定[" + other.getUserName() + "(" + other.getJobNo()
                            + ")]，已解绑并改绑用户[" + user.getUserName() + "(" + user.getJobNo() + ")]", 1);
        }
        boolean alreadyBound = openid.equals(user.getMpOpenid());
        if (!alreadyBound) {
            OrgUser update = new OrgUser();
            update.setId(userId);
            update.setMpOpenid(openid);
            orgUserService.updateById(update);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openidMasked", maskOpenid(openid));
        result.put("alreadyBound", alreadyBound);
        return result;
    }

    /** openid 脱敏：保留前 3 后 3（oXy***3kQ） */
    static String maskOpenid(String openid) {
        if (openid == null) {
            return null;
        }
        String s = openid.trim();
        return s.length() > 6 ? s.substring(0, 3) + "***" + s.substring(s.length() - 3) : "***";
    }

    private static boolean isPlaceholder(String configValue) {
        return configValue == null || configValue.isBlank() || configValue.trim().startsWith("<");
    }

    // ---------------------------------------------------------------- NOTIFY-WX-02 查询订阅授权状态

    public Map<String, Object> subscribeStatus() {
        Long userId = SecurityUtils.getUserId();
        OrgUser user = orgUserService.getById(userId);
        boolean wxBound = user != null && StringUtils.hasText(user.getMpOpenid());
        Map<Object, Object> quota = redisTemplate.opsForHash().entries(SUB_HASH_PREFIX + userId);

        List<Map<String, Object>> scenes = new ArrayList<>();
        SCENES.forEach((sceneCode, def) -> {
            int remain = quota.get(sceneCode) instanceof Number n ? n.intValue() : 0;
            Map<String, Object> s = new LinkedHashMap<String, Object>();
            s.put("scene", sceneCode);
            s.put("tplId", sysConfigService.getValue(def.tplConfigKey()));
            s.put("subscribed", remain > 0);
            s.put("remainCount", remain);
            scenes.add(s);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wxBound", wxBound);
        result.put("scenes", scenes);
        return result;
    }

    // ---------------------------------------------------------------- NOTIFY-WX-03 上报订阅授权结果（幂等：重复上报不累加）

    public Map<String, Object> reportSubscribe(SubscribeReportReq req) {
        Long userId = SecurityUtils.getUserId();
        if (req == null || req.getResults() == null || req.getResults().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "results 不能为空");
        }
        int updated = 0;
        for (SubscribeReportReq.Item item : req.getResults()) {
            if (item == null || !StringUtils.hasText(item.getScene())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "scene 不能为空");
            }
            String scene = item.getScene().trim().toUpperCase();
            SceneDef def = SCENES.get(scene);
            if (def == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "场景编码无法识别: " + item.getScene());
            }
            String configuredTpl = sysConfigService.getValue(def.tplConfigKey());
            if (!StringUtils.hasText(item.getTplId()) || !item.getTplId().trim().equals(configuredTpl)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "tplId 与系统参数配置不一致: scene=" + scene);
            }
            String action = item.getAction() == null ? "" : item.getAction().trim().toLowerCase();
            switch (action) {
                case "accept" -> {
                    // 一次性订阅约束：一次授权 = 一次下发；同场景重复上报不累加
                    Boolean fresh = redisTemplate.opsForValue().setIfAbsent(
                            SUB_ACCEPT_PREFIX + userId + ":" + scene, "1");
                    if (Boolean.TRUE.equals(fresh)) {
                        redisTemplate.opsForHash().increment(SUB_HASH_PREFIX + userId, scene, 1);
                    }
                    updated++;
                }
                case "reject", "ban" -> updated++;
                default -> throw new BizException(ErrorCode.PARAM_ERROR, "action 仅支持 accept/reject/ban");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        return result;
    }

    // ---------------------------------------------------------------- NOTIFY-WX-04 推送记录查询（管理端）

    public PageResult<Map<String, Object>> pageRecords(Long examId, String scene, String status, String keyword,
                                                       LocalDateTime startTime, LocalDateTime endTime,
                                                       long pageNum, long pageSize) {
        QueryWrapper<NotifyWxLog> qw = new QueryWrapper<>();
        if (examId != null) {
            qw.eq("biz_type", "session").eq("biz_id", examId);
        }
        if (StringUtils.hasText(scene)) {
            SceneDef def = SCENES.get(scene.trim().toUpperCase());
            if (def == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "场景编码无法识别: " + scene);
            }
            qw.eq("scene", def.code());
        }
        if (StringUtils.hasText(status)) {
            Integer statusCode = SEND_STATUS_API.entrySet().stream()
                    .filter(e -> e.getValue().equalsIgnoreCase(status.trim()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (statusCode == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "status 仅支持 PENDING/SUCCESS/FAILED/GIVE_UP");
            }
            qw.eq("send_status", statusCode);
        }
        if (StringUtils.hasText(keyword)) {
            List<Long> receiverIds = orgUserService.list(new QueryWrapper<OrgUser>()
                            .select("id")
                            .and(w -> w.like("user_name", keyword.trim()).or().like("job_no", keyword.trim())))
                    .stream().map(OrgUser::getId).toList();
            if (receiverIds.isEmpty()) {
                return PageResult.of(0, List.of(), pageNum, pageSize);
            }
            qw.in("receiver_id", receiverIds);
        }
        qw.ge(startTime != null, "create_time", startTime);
        qw.le(endTime != null, "create_time", endTime);

        // 数据权限：按场次创建方组织路径过滤（越权 10004 由 inSql 子查询天然收口为空集）
        String prefix = SecurityUtils.dataScopePathPrefix();
        if (prefix != null) {
            qw.eq("biz_type", "session");
            qw.inSql("biz_id", "SELECT id FROM t_exam_session WHERE deleted = 0 AND create_org_path LIKE '"
                    + prefix.replace("'", "''") + "%'");
        }
        qw.orderByDesc("id");
        Page<NotifyWxLog> page = page(new Page<>(pageNum, pageSize), qw);

        List<Long> sessionIds = page.getRecords().stream()
                .filter(r -> "session".equals(r.getBizType()) && r.getBizId() != null)
                .map(NotifyWxLog::getBizId).distinct().toList();
        Map<Long, ExamSession> sessionMap = sessionIds.isEmpty() ? Map.of()
                : examSessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(ExamSession::getId, Function.identity()));
        List<Long> userIds = page.getRecords().stream().map(NotifyWxLog::getReceiverId).distinct().toList();
        Map<Long, OrgUser> userMap = userIds.isEmpty() ? Map.of()
                : orgUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(OrgUser::getId, Function.identity()));

        return PageResult.of(page).map(r -> toRecordItem(r, sessionMap, userMap));
    }

    private Map<String, Object> toRecordItem(NotifyWxLog r, Map<Long, ExamSession> sessionMap,
                                             Map<Long, OrgUser> userMap) {
        OrgUser user = userMap.get(r.getReceiverId());
        ExamSession session = "session".equals(r.getBizType()) ? sessionMap.get(r.getBizId()) : null;
        OrgUnit unit = user == null || user.getOrgId() == null ? null : orgUnitService.getUnit(user.getOrgId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordId", r.getId());
        m.put("noticeId", r.getNoticeId());
        m.put("scene", SCENES.entrySet().stream().filter(e -> e.getValue().code() == (r.getScene() == null ? -1 : r.getScene()))
                .map(Map.Entry::getKey).findFirst().orElse(String.valueOf(r.getScene())));
        m.put("examId", session == null ? null : session.getId());
        m.put("examName", session == null ? null : session.getSessionName());
        m.put("userId", r.getReceiverId());
        m.put("name", user == null ? null : user.getUserName());
        m.put("jobNo", user == null ? null : user.getJobNo());
        m.put("orgName", unit == null ? null : unit.getUnitName());
        m.put("mpOpenidMasked", maskOpenid(r.getMpOpenid()));
        m.put("templateId", r.getTemplateId());
        m.put("status", SEND_STATUS_API.getOrDefault(r.getSendStatus(), String.valueOf(r.getSendStatus())));
        m.put("errcode", r.getErrcode());
        m.put("errmsg", r.getErrmsg());
        m.put("retryOf", r.getRetryOf());
        m.put("sendTime", r.getSendTime());
        m.put("createdTime", r.getCreateTime());
        return m;
    }

    // ---------------------------------------------------------------- 内部工具

    /** 按用户限流：10 次/分钟（口径同登录限流），超限抛 10006 */
    private void checkRateLimit(Long userId) {
        String key = RATE_LIMIT_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > RATE_LIMIT_MAX) {
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
    }
}
