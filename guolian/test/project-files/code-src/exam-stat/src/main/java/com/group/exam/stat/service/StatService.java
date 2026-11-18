package com.group.exam.stat.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.common.util.JsonUtils;
import com.group.exam.exam.entity.AnswerDetail;
import com.group.exam.exam.entity.AnswerSheet;
import com.group.exam.exam.entity.ExamCandidate;
import com.group.exam.exam.entity.ExamSession;
import com.group.exam.exam.entity.StatExamUnit;
import com.group.exam.exam.mapper.AnswerDetailMapper;
import com.group.exam.exam.mapper.AnswerSheetMapper;
import com.group.exam.exam.mapper.ExamCandidateMapper;
import com.group.exam.exam.mapper.ExamSessionMapper;
import com.group.exam.exam.mapper.StatExamUnitMapper;
import com.group.exam.exam.support.RandomStrategy;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.QuesTypeEnum;
import com.group.exam.question.service.QuesItemService;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.SysConfigService;
import com.group.exam.system.service.SysLogService;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试统计分析（STAT-01 ~ STAT-08）
 *
 * <p>口径说明：
 * <ul>
 *   <li>应考/实考/缺考：以 t_exam_candidate 名单快照 + t_answer_sheet 为准（与 ScorePublishService 写入口径一致）；</li>
 *   <li>分数类指标：取 mark_status>=4（已合成/已发布）且 total_score 非空的答卷，未发布场次同样可看实时口径；</li>
 *   <li>等级分布（STAT-05）：取 mark_status=5 已发布答卷的 grade_name 快照，未发布场次返回空列表；</li>
 *   <li>单位汇总（STAT-02/03）：读 t_stat_exam_unit 预聚合快照（成绩发布时生成），未发布时 children 为空；</li>
 *   <li>所有 rate/ratio 字段统一为 0~1 小数（scale 4）。</li>
 * </ul>
 */
@Service
public class StatService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_SEGMENTS = "0-59,60-69,70-79,80-89,90-100";
    /** 成绩已发布（t_exam_session.session_status=3） */
    private static final int STATUS_PUBLISHED = 3;
    /** 答卷 mark_status：4成绩已合成 5已发布 */
    private static final int MARK_SYNTHESIZED = 4;
    private static final int MARK_PUBLISHED = 5;

    private final ExamSessionMapper examSessionMapper;
    private final ExamCandidateMapper examCandidateMapper;
    private final AnswerSheetMapper answerSheetMapper;
    private final AnswerDetailMapper answerDetailMapper;
    private final StatExamUnitMapper statExamUnitMapper;
    private final OrgUnitService orgUnitService;
    private final SysConfigService sysConfigService;
    private final SysLogService sysLogService;
    private final QuesItemService quesItemService;

    public StatService(ExamSessionMapper examSessionMapper, ExamCandidateMapper examCandidateMapper,
                       AnswerSheetMapper answerSheetMapper, AnswerDetailMapper answerDetailMapper,
                       StatExamUnitMapper statExamUnitMapper, OrgUnitService orgUnitService,
                       SysConfigService sysConfigService, SysLogService sysLogService,
                       QuesItemService quesItemService) {
        this.examSessionMapper = examSessionMapper;
        this.examCandidateMapper = examCandidateMapper;
        this.answerSheetMapper = answerSheetMapper;
        this.answerDetailMapper = answerDetailMapper;
        this.statExamUnitMapper = statExamUnitMapper;
        this.orgUnitService = orgUnitService;
        this.sysConfigService = sysConfigService;
        this.sysLogService = sysLogService;
        this.quesItemService = quesItemService;
    }

    // ---------------------------------------------------------------- 公共校验

    /** 场次存在且在当前登录人数据权限范围内，否则抛 10005/10004 */
    public ExamSession requireSession(Long examId) {
        ExamSession session = examSessionMapper.selectById(examId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考试场次不存在: " + examId);
        }
        SecurityUtils.checkOrgScope(session.getCreateOrgPath());
        return session;
    }

    /** orgId 为空时取当前登录人数据权限根节点；单位不存在 60001，越权 10004 */
    private OrgUnit resolveUnitInScope(Long orgId) {
        Long id = orgId != null ? orgId : SecurityUtils.getLoginUser().getOrgId();
        OrgUnit unit = orgUnitService.getUnit(id);
        if (unit == null) {
            throw new BizException(ErrorCode.STAT_PARAM_ERROR, "单位不存在: " + id);
        }
        SecurityUtils.checkOrgScope(unit.getPath());
        return unit;
    }

    private List<ExamCandidate> scopedCandidates(Long sessionId, String pathPrefix) {
        QueryWrapper<ExamCandidate> qw = new QueryWrapper<ExamCandidate>().eq("session_id", sessionId);
        qw.likeRight(pathPrefix != null, "org_path", pathPrefix);
        return examCandidateMapper.selectList(qw);
    }

    private List<AnswerSheet> scopedSheets(Long sessionId, String pathPrefix) {
        QueryWrapper<AnswerSheet> qw = new QueryWrapper<AnswerSheet>().eq("session_id", sessionId);
        qw.likeRight(pathPrefix != null, "org_path", pathPrefix);
        return answerSheetMapper.selectList(qw);
    }

    private static BigDecimal passScoreOf(ExamSession session) {
        Map<String, Object> snapshot = session.getPaperStrategySnapshot();
        return snapshot == null ? null : RandomStrategy.asBigDecimal(snapshot.get("passScore"));
    }

    // ---------------------------------------------------------------- STAT-01 单场总览

    public Map<String, Object> overview(Long examId) {
        ExamSession session = requireSession(examId);
        String prefix = SecurityUtils.dataScopePathPrefix();
        List<ExamCandidate> candidates = scopedCandidates(examId, prefix);
        List<AnswerSheet> sheets = scopedSheets(examId, prefix);

        int shouldCount = candidates.size();
        long attendedCount = candidates.stream()
                .filter(c -> c.getCandidateStatus() != null
                        && (c.getCandidateStatus() == 1 || c.getCandidateStatus() == 3))
                .count();
        List<BigDecimal> scores = gradedScores(sheets);
        BigDecimal passScore = passScoreOf(session);
        long passCount = passScore == null ? 0
                : scores.stream().filter(s -> s.compareTo(passScore) >= 0).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", session.getId());
        result.put("examName", session.getSessionName());
        result.put("shouldCount", shouldCount);
        result.put("attendedCount", attendedCount);
        result.put("absentCount", shouldCount - attendedCount);
        result.put("attendRate", ratio(attendedCount, shouldCount));
        result.put("avgScore", avg(scores));
        result.put("maxScore", scores.stream().max(BigDecimal::compareTo).orElse(null));
        result.put("minScore", scores.stream().min(BigDecimal::compareTo).orElse(null));
        result.put("passCount", passCount);
        result.put("passRate", ratio(passCount, scores.size()));
        result.put("gradeDist", gradeDistOf(sheets.stream()
                .filter(s -> s.getMarkStatus() != null && s.getMarkStatus() >= MARK_SYNTHESIZED)
                .toList(), true));
        result.put("status", session.getSessionStatus());
        result.put("scorePublishTime", session.getScorePublishTime() == null
                ? null : session.getScorePublishTime().format(DT));
        return result;
    }

    /** mark_status>=4 且 total_score 非空的成绩集合 */
    private static List<BigDecimal> gradedScores(List<AnswerSheet> sheets) {
        return sheets.stream()
                .filter(s -> s.getMarkStatus() != null && s.getMarkStatus() >= MARK_SYNTHESIZED
                        && s.getTotalScore() != null)
                .map(AnswerSheet::getTotalScore)
                .toList();
    }

    // ---------------------------------------------------------------- STAT-02 分层级单位汇总

    public Map<String, Object> orgSummary(Long examId, Long orgId) {
        ExamSession session = requireSession(examId);
        OrgUnit root = resolveUnitInScope(orgId);
        return buildOrgSummary(session, root);
    }

    /** 读 t_stat_exam_unit 快照，按 root 直属下级单位递归聚合 */
    private Map<String, Object> buildOrgSummary(ExamSession session, OrgUnit root) {
        List<OrgUnit> children = orgUnitService.list(new QueryWrapper<OrgUnit>()
                .eq("parent_id", root.getId()).orderByAsc("sort_no").orderByAsc("id"));
        List<StatExamUnit> rows = statExamUnitMapper.selectList(new QueryWrapper<StatExamUnit>()
                .eq("session_id", session.getId())
                .likeRight("org_path", root.getPath()));

        List<Map<String, Object>> childList = new ArrayList<>();
        for (OrgUnit child : children) {
            String childPath = child.getPath();
            List<StatExamUnit> sub = rows.stream()
                    .filter(r -> r.getOrgPath() != null && r.getOrgPath().startsWith(childPath))
                    .toList();
            long should = sub.stream().mapToLong(r -> nvl(r.getShouldCount())).sum();
            long actual = sub.stream().mapToLong(r -> nvl(r.getActualCount())).sum();
            long absent = sub.stream().mapToLong(r -> nvl(r.getAbsentCount())).sum();
            long pass = sub.stream().mapToLong(r -> nvl(r.getPassCount())).sum();
            // 加权平均分：Σ(avg×actual)/Σactual
            BigDecimal weightedSum = BigDecimal.ZERO;
            long weightedCnt = 0;
            Map<String, Integer> gradeMerge = new LinkedHashMap<>();
            for (StatExamUnit r : sub) {
                if (r.getAvgScore() != null && r.getActualCount() != null && r.getActualCount() > 0) {
                    weightedSum = weightedSum.add(r.getAvgScore().multiply(BigDecimal.valueOf(r.getActualCount())));
                    weightedCnt += r.getActualCount();
                }
                if (r.getGradeDist() != null) {
                    r.getGradeDist().forEach((k, v) ->
                            gradeMerge.merge(k, v instanceof Number n ? n.intValue() : 0, Integer::sum));
                }
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("orgId", child.getId());
            entry.put("orgName", child.getUnitName());
            entry.put("shouldCount", should);
            entry.put("attendedCount", actual);
            entry.put("absentCount", absent);
            entry.put("attendRate", ratio(actual, should));
            entry.put("avgScore", weightedCnt == 0 ? null
                    : weightedSum.divide(BigDecimal.valueOf(weightedCnt), 2, RoundingMode.HALF_UP));
            entry.put("passRate", ratio(pass, actual));
            entry.put("gradeDist", gradeMerge.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(e -> {
                        Map<String, Object> g = new LinkedHashMap<>();
                        g.put("gradeName", e.getKey());
                        g.put("count", e.getValue());
                        return g;
                    }).toList());
            childList.add(entry);
        }
        // 同级排名：按平均分降序（无成绩排末位），同分同名次
        List<Map<String, Object>> ranked = new ArrayList<>(childList);
        ranked.sort(Comparator.comparing(m -> (BigDecimal) m.get("avgScore"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        int rank = 0;
        BigDecimal prev = null;
        for (Map<String, Object> entry : ranked) {
            BigDecimal avgScore = (BigDecimal) entry.get("avgScore");
            if (avgScore == null) {
                entry.put("rank", null);
                continue;
            }
            rank = (prev != null && avgScore.compareTo(prev) == 0) ? rank : rank + 1;
            prev = avgScore;
            entry.put("rank", rank);
        }

        boolean hasSubLevel = false;
        if (!children.isEmpty()) {
            hasSubLevel = orgUnitService.count(new QueryWrapper<OrgUnit>()
                    .in("parent_id", children.stream().map(OrgUnit::getId).toList())) > 0;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orgId", root.getId());
        result.put("orgName", root.getUnitName());
        result.put("children", ranked);
        result.put("hasSubLevel", hasSubLevel);
        return result;
    }

    // ---------------------------------------------------------------- STAT-03 单位下钻

    public Object orgDrill(Long examId, Long orgId, String detailType, long pageNum, long pageSize) {
        ExamSession session = requireSession(examId);
        OrgUnit unit = orgUnitService.getUnit(orgId);
        if (unit == null) {
            throw new BizException(ErrorCode.STAT_PARAM_ERROR, "单位不存在: " + orgId);
        }
        SecurityUtils.checkOrgScope(unit.getPath());
        String type = detailType == null || detailType.isBlank() ? "SUB_ORG" : detailType;
        if ("SUB_ORG".equals(type)) {
            return buildOrgSummary(session, unit);
        }
        if (!"USER".equals(type)) {
            throw new BizException(ErrorCode.STAT_PARAM_ERROR, "detailType 仅支持 SUB_ORG/USER");
        }
        List<Map<String, Object>> rows = userRowsOfOrg(session, unit);
        int total = rows.size();
        int from = (int) Math.min((pageNum - 1) * pageSize, total);
        int to = (int) Math.min(from + pageSize, total);
        return PageResult.of(total, rows.subList(from, to), pageNum, pageSize);
    }

    /** 单位（名单快照口径，org_id 精确匹配）人员成绩明细，按成绩降序、同分按用时升序 */
    private List<Map<String, Object>> userRowsOfOrg(ExamSession session, OrgUnit unit) {
        List<ExamCandidate> candidates = examCandidateMapper.selectList(new QueryWrapper<ExamCandidate>()
                .eq("session_id", session.getId()).eq("org_id", unit.getId()));
        Map<Long, AnswerSheet> sheetByUser = answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                        .eq("session_id", session.getId()).eq("org_id", unit.getId()))
                .stream().collect(Collectors.toMap(AnswerSheet::getUserId, Function.identity(), (a, b) -> a));
        return buildUserRows(candidates, sheetByUser, passScoreOf(session));
    }

    /**
     * 排名键（V1.1 口径）：分数降序，同分按答题用时 durationSec 升序（NULL 用时视为最长）。
     * 分数与用时均相同视为并列同名次。
     */
    private record RankKey(BigDecimal score, Integer durationSec) {
    }

    private static final Comparator<RankKey> RANK_ORDER = Comparator
            .comparing(RankKey::score, Comparator.reverseOrder())
            .thenComparing(k -> k.durationSec() == null ? Integer.MAX_VALUE : k.durationSec());

    /** 竞争排名：1 + 严格排在前面（分数更高，或同分用时更短）的人数 */
    private static int rankOf(List<RankKey> sorted, RankKey target) {
        int rank = 1;
        for (RankKey k : sorted) {
            if (RANK_ORDER.compare(k, target) < 0) {
                rank++;
            } else {
                break;
            }
        }
        return rank;
    }

    /** 组装人员明细行并计算单位内排名（分数降序、同分按用时升序、完全同名次、无成绩 rank 为 null 排末位） */
    private List<Map<String, Object>> buildUserRows(List<ExamCandidate> candidates,
                                                    Map<Long, AnswerSheet> sheetByUser,
                                                    BigDecimal passScore) {
        List<RankKey> rankedKeys = candidates.stream()
                .map(c -> sheetByUser.get(c.getUserId()))
                .filter(Objects::nonNull)
                .filter(s -> s.getMarkStatus() != null && s.getMarkStatus() >= MARK_SYNTHESIZED
                        && s.getTotalScore() != null)
                .map(s -> new RankKey(s.getTotalScore(), s.getDurationSec()))
                .sorted(RANK_ORDER)
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExamCandidate c : candidates) {
            AnswerSheet sheet = sheetByUser.get(c.getUserId());
            boolean graded = sheet != null && sheet.getMarkStatus() != null
                    && sheet.getMarkStatus() >= MARK_SYNTHESIZED && sheet.getTotalScore() != null;
            BigDecimal score = graded ? sheet.getTotalScore() : null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", c.getUserId());
            row.put("name", c.getUserName());
            row.put("jobNo", c.getJobNo());
            row.put("orgName", c.getOrgName());
            row.put("score", score);
            row.put("grade", graded ? sheet.getGradeName() : null);
            row.put("passed", score == null || passScore == null ? null : score.compareTo(passScore) >= 0);
            row.put("durationSec", sheet == null ? null : sheet.getDurationSec());
            row.put("rankInOrg", score == null ? null
                    : rankOf(rankedKeys, new RankKey(score, sheet.getDurationSec())));
            row.put("sheetStatus", sheet == null ? null : sheet.getSheetStatus());
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("score"),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> (Integer) r.get("durationSec"),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> String.valueOf(r.get("jobNo"))));
        return rows;
    }

    // ---------------------------------------------------------------- STAT-09 单位平均分排名（V1.2）

    /**
     * 单位排名：orgId 缺省取当前管理员数据权限根单位，排名范围为其直属下级单位；
     * 仅统计已合成成绩答卷（mark_status>=4 且 total_score 非空），avgScore=AVG(total_score) 保留 1 位小数，
     * rank 按 avgScore 降序 RANK() 语义（同分并列、跳名次，无成绩单位排末位 rank=null）。
     */
    public Map<String, Object> orgRanking(Long examId, Long orgId) {
        ExamSession session = requireSession(examId);
        OrgUnit root = resolveUnitInScope(orgId);
        List<OrgUnit> children = orgUnitService.list(new QueryWrapper<OrgUnit>()
                .eq("parent_id", root.getId()).orderByAsc("sort_no").orderByAsc("id"));
        List<AnswerSheet> sheets = answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                .select("id", "org_path", "total_score")
                .eq("session_id", session.getId())
                .ge("mark_status", MARK_SYNTHESIZED)
                .isNotNull("total_score"));

        List<Map<String, Object>> list = new ArrayList<>();
        for (OrgUnit child : children) {
            List<BigDecimal> scores = sheets.stream()
                    .filter(s -> s.getOrgPath() != null && s.getOrgPath().startsWith(child.getPath()))
                    .map(AnswerSheet::getTotalScore)
                    .toList();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("orgId", child.getId());
            entry.put("orgName", child.getUnitName());
            entry.put("avgScore", scores.isEmpty() ? null
                    : scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP));
            entry.put("participantCount", scores.size());
            list.add(entry);
        }
        // RANK() 语义：同分并列同名次、名次跳号；无成绩 NULLS LAST
        list.sort(Comparator.comparing(m -> (BigDecimal) m.get("avgScore"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        int rank = 0;
        BigDecimal prev = null;
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> entry = list.get(i);
            BigDecimal avgScore = (BigDecimal) entry.get("avgScore");
            if (avgScore == null) {
                entry.put("rank", null);
                continue;
            }
            rank = (prev != null && avgScore.compareTo(prev) == 0) ? rank : i + 1;
            prev = avgScore;
            entry.put("rank", rank);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", session.getId());
        result.put("examName", session.getSessionName());
        result.put("list", list);
        return result;
    }

    // ---------------------------------------------------------------- STAT-04 分数段分布

    public List<Map<String, Object>> scoreSegments(Long examId, Long orgId, String segments) {
        ExamSession session = requireSession(examId);
        List<Segment> segs = parseSegments(segments);
        String pathPrefix;
        if (orgId != null) {
            OrgUnit unit = orgUnitService.getUnit(orgId);
            if (unit == null) {
                throw new BizException(ErrorCode.STAT_PARAM_ERROR, "单位不存在: " + orgId);
            }
            SecurityUtils.checkOrgScope(unit.getPath());
            pathPrefix = unit.getPath();
        } else {
            pathPrefix = SecurityUtils.dataScopePathPrefix();
        }
        List<AnswerSheet> sheets = answerSheetMapper.selectList(segmentSheetQuery(examId, pathPrefix));

        long[] counts = new long[segs.size()];
        long total = 0;
        for (AnswerSheet sheet : sheets) {
            double v = sheet.getTotalScore().doubleValue();
            for (int i = 0; i < segs.size(); i++) {
                Segment seg = segs.get(i);
                if (v >= seg.min && v <= seg.max) {
                    counts[i]++;
                    total++;
                    break;
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < segs.size(); i++) {
            Segment seg = segs.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("segment", seg.label);
            item.put("minScore", seg.min);
            item.put("maxScore", seg.max);
            item.put("count", counts[i]);
            item.put("ratio", ratio(counts[i], total));
            result.add(item);
        }
        return result;
    }

    private QueryWrapper<AnswerSheet> segmentSheetQuery(Long examId, String pathPrefix) {
        QueryWrapper<AnswerSheet> qw = new QueryWrapper<AnswerSheet>()
                .eq("session_id", examId)
                .ge("mark_status", MARK_SYNTHESIZED)
                .isNotNull("total_score");
        qw.likeRight(pathPrefix != null, "org_path", pathPrefix);
        return qw;
    }

    private String segmentConfig() {
        String config = sysConfigService.getValue("exam.scoreSegmentDefault");
        return config == null || config.isBlank() ? DEFAULT_SEGMENTS : config;
    }

    /** 分数段：label + 闭区间 [min, max] */
    private record Segment(String label, double min, double max) {
    }

    /**
     * 解析分数段配置，支持两种格式：
     * 1. JSON（系统参数 exam.scoreSegmentDefault 的文档格式）: [{"label":"0-59","min":0,"max":59.99},...]
     * 2. 简写: "0-59,60-69,..."（非末段按 b+0.99 闭合，100 封顶）
     * 非法格式抛 60001
     */
    private List<Segment> parseSegments(String segments) {
        String text = segments == null || segments.isBlank() ? segmentConfig() : segments;
        List<Segment> bounds = new ArrayList<>();
        if (text.trim().startsWith("[")) {
            try {
                for (Map<String, Object> node : JsonUtils.fromJson(text, new TypeReference<List<Map<String, Object>>>() {
                })) {
                    Object label = node.get("label");
                    Object min = node.get("min");
                    Object max = node.get("max");
                    if (label == null || !(min instanceof Number) || !(max instanceof Number)) {
                        throw new BizException(ErrorCode.STAT_PARAM_ERROR, "非法分数段: " + node);
                    }
                    double lo = ((Number) min).doubleValue();
                    double hi = ((Number) max).doubleValue();
                    if (lo < 0 || hi < lo) {
                        throw new BizException(ErrorCode.STAT_PARAM_ERROR, "非法分数段: " + node);
                    }
                    bounds.add(new Segment(label.toString(), lo, hi));
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(ErrorCode.STAT_PARAM_ERROR, "非法分数段: " + text);
            }
        } else {
            for (String part : text.split(",")) {
                String[] pair = part.trim().split("-");
                if (pair.length != 2) {
                    throw new BizException(ErrorCode.STAT_PARAM_ERROR, "非法分数段: " + part);
                }
                try {
                    double lo = Double.parseDouble(pair[0].trim());
                    double hi = Double.parseDouble(pair[1].trim());
                    if (lo < 0 || hi < lo) {
                        throw new BizException(ErrorCode.STAT_PARAM_ERROR, "非法分数段: " + part);
                    }
                    bounds.add(new Segment(part.trim(), lo, hi >= 100 ? 100 : hi + 0.99));
                } catch (NumberFormatException e) {
                    throw new BizException(ErrorCode.STAT_PARAM_ERROR, "非法分数段: " + part);
                }
            }
        }
        if (bounds.isEmpty()) {
            throw new BizException(ErrorCode.STAT_PARAM_ERROR, "分数段配置为空");
        }
        return bounds;
    }

    // ---------------------------------------------------------------- STAT-05 等级分布

    public List<Map<String, Object>> gradeDistribution(Long examId, Long orgId) {
        ExamSession session = requireSession(examId);
        String pathPrefix;
        if (orgId != null) {
            OrgUnit unit = orgUnitService.getUnit(orgId);
            if (unit == null) {
                throw new BizException(ErrorCode.STAT_PARAM_ERROR, "单位不存在: " + orgId);
            }
            SecurityUtils.checkOrgScope(unit.getPath());
            pathPrefix = unit.getPath();
        } else {
            pathPrefix = SecurityUtils.dataScopePathPrefix();
        }
        // 口径：成绩发布时等级快照（mark_status=5），未发布场次返回空
        QueryWrapper<AnswerSheet> qw = new QueryWrapper<AnswerSheet>()
                .eq("session_id", session.getId()).eq("mark_status", MARK_PUBLISHED);
        qw.likeRight(pathPrefix != null, "org_path", pathPrefix);
        List<AnswerSheet> published = answerSheetMapper.selectList(qw);
        return gradeDistOf(published, true);
    }

    /** 等级分布：[{gradeName,count,ratio}]，按人数降序；withRatio=false 时仅 gradeName/count */
    private static List<Map<String, Object>> gradeDistOf(List<AnswerSheet> sheets, boolean withRatio) {
        Map<String, Long> counter = sheets.stream()
                .filter(s -> s.getGradeName() != null)
                .collect(Collectors.groupingBy(AnswerSheet::getGradeName, Collectors.counting()));
        long total = sheets.size();
        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> g = new LinkedHashMap<String, Object>();
                    g.put("gradeName", e.getKey());
                    g.put("count", e.getValue());
                    if (withRatio) {
                        g.put("ratio", ratio(e.getValue(), total));
                    }
                    return g;
                })
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- STAT-06 题目维度分析

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> questionAnalysis(Long examId, String orderBy, Integer topN) {
        ExamSession session = requireSession(examId);
        String order = orderBy == null || orderBy.isBlank() ? "SEQ" : orderBy;
        if (!"SEQ".equals(order) && !"WRONG_DESC".equals(order)) {
            throw new BizException(ErrorCode.STAT_PARAM_ERROR, "orderBy 仅支持 SEQ/WRONG_DESC");
        }
        String prefix = SecurityUtils.dataScopePathPrefix();
        List<AnswerSheet> sheets = scopedSheets(examId, prefix);
        if (sheets.isEmpty()) {
            return List.of();
        }
        List<Long> sheetIds = sheets.stream().map(AnswerSheet::getId).toList();
        List<AnswerDetail> details = answerDetailMapper.selectList(new QueryWrapper<AnswerDetail>()
                .in("sheet_id", sheetIds));
        if (details.isEmpty()) {
            return List.of();
        }
        // 题目快照（题干/选项，first-wins；RANDOM 一人一卷按 quesId 归并）
        Map<Long, Map<String, Object>> quesMeta = new HashMap<>();
        for (AnswerSheet sheet : sheets) {
            Map<String, Object> snapshot = sheet.getPaperSnapshot();
            if (snapshot == null || !(snapshot.get("questions") instanceof List<?> questions)) {
                continue;
            }
            for (Object o : questions) {
                if (o instanceof Map<?, ?> q) {
                    Long quesId = RandomStrategy.asLong(q.get("quesId"));
                    if (quesId != null) {
                        quesMeta.putIfAbsent(quesId, (Map<String, Object>) q);
                    }
                }
            }
        }
        // 知识点标签：题库题目冗余（快照不含 tags）
        List<Long> quesIds = details.stream().map(AnswerDetail::getQuesId).distinct().toList();
        Map<Long, List<String>> tagsByQues = quesItemService.listByIds(quesIds).stream()
                .collect(Collectors.toMap(QuesItem::getId,
                        item -> item.getKnowledgeTags() == null ? List.of() : item.getKnowledgeTags(),
                        (a, b) -> a));

        Map<Long, List<AnswerDetail>> byQues = details.stream()
                .collect(Collectors.groupingBy(AnswerDetail::getQuesId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<AnswerDetail>> entry : byQues.entrySet()) {
            Long quesId = entry.getKey();
            List<AnswerDetail> list = entry.getValue();
            List<AnswerDetail> judged = list.stream().filter(d -> d.getIsRight() != null).toList();
            long right = judged.stream().filter(d -> d.getIsRight() == 1).count();
            long half = judged.stream().filter(d -> d.getIsRight() == 2).count();
            long wrong = judged.stream().filter(d -> d.getIsRight() == 0).count();
            BigDecimal avgScore = avg(judged.stream().map(AnswerDetail::getScore)
                    .filter(Objects::nonNull).toList());
            BigDecimal fullScore = list.stream().map(AnswerDetail::getQuesScore)
                    .filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
            Map<String, Object> meta = quesMeta.getOrDefault(quesId, Map.of());
            int quesType = list.get(0).getQuesType() == null ? 0 : list.get(0).getQuesType();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", list.stream().map(AnswerDetail::getSortNo).filter(Objects::nonNull)
                    .min(Integer::compareTo).orElse(0));
            row.put("sheetQuesId", quesId);
            row.put("quesType", quesTypeName(quesType));
            row.put("stemText", stemText(RandomStrategy.asString(meta.get("stem"))));
            row.put("fullScore", fullScore);
            row.put("avgScore", avgScore);
            row.put("correctRate", ratio(right, judged.size()));
            row.put("halfRate", ratio(half, judged.size()));
            row.put("wrongRate", ratio(wrong, judged.size()));
            row.put("scoreRate", avgScore == null || fullScore == null
                    || fullScore.compareTo(BigDecimal.ZERO) == 0 ? null
                    : avgScore.divide(fullScore, 4, RoundingMode.HALF_UP));
            row.put("tags", tagsByQues.getOrDefault(quesId, List.of()));
            row.put("optionDist", optionDist(list, quesType));
            result.add(row);
        }
        if ("WRONG_DESC".equals(order)) {
            result.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("wrongRate"))
                    .reversed().thenComparing(r -> (Integer) r.get("seq")));
        } else {
            result.sort(Comparator.comparing(r -> (Integer) r.get("seq")));
        }
        if (topN != null && topN > 0 && result.size() > topN) {
            return new ArrayList<>(result.subList(0, topN));
        }
        return result;
    }

    /** 选择/判断题选项选中分布：按展示 key 统计，ratio=选中人数/作答人数 */
    private static List<Map<String, Object>> optionDist(List<AnswerDetail> details, int quesType) {
        if (quesType != 1 && quesType != 2 && quesType != 3) {
            return List.of();
        }
        Map<String, Long> counter = new HashMap<>();
        long answered = 0;
        for (AnswerDetail d : details) {
            if (d.getUserAnswer() != null && d.getUserAnswer().get("keys") instanceof List<?> keys
                    && !keys.isEmpty()) {
                answered++;
                for (Object key : keys) {
                    counter.merge(String.valueOf(key), 1L, Long::sum);
                }
            }
        }
        if (counter.isEmpty()) {
            return List.of();
        }
        long denominator = answered;
        return counter.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> opt = new LinkedHashMap<String, Object>();
                    opt.put("optKey", e.getKey());
                    opt.put("selectCount", e.getValue());
                    opt.put("ratio", ratio(e.getValue(), denominator));
                    return opt;
                })
                .collect(Collectors.toList());
    }

    private static String quesTypeName(int quesType) {
        return switch (quesType) {
            case 1 -> "SINGLE";
            case 2 -> "MULTI";
            case 3 -> "JUDGE";
            case 4 -> "FILL";
            case 5 -> "ESSAY";
            default -> String.valueOf(quesType);
        };
    }

    /** 导出用题型中文名（单选题/多选题/判断题/填空题/简答题），复用 QuesTypeEnum 映射 */
    private static String quesTypeCnName(Object quesType) {
        QuesTypeEnum e = QuesTypeEnum.parseOrNull(quesType == null ? null : String.valueOf(quesType));
        return e == null ? String.valueOf(quesType) : e.getCnName() + "题";
    }

    /** 题干富文本 → 纯文本摘要（≤100 字） */
    private static String stemText(String stem) {
        if (stem == null) {
            return null;
        }
        String text = stem.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
        return text.length() <= 100 ? text : text.substring(0, 100);
    }

    // ---------------------------------------------------------------- STAT-07 统计报表导出

    public void export(Long examId, String exportType, Long orgId, OutputStream out) {
        ExamSession session = requireSession(examId);
        List<List<String>> head;
        List<List<Object>> rows = new ArrayList<>();
        switch (exportType == null ? "" : exportType) {
            case "SUMMARY" -> {
                OrgUnit root = resolveUnitInScope(orgId);
                head = List.of(List.of("单位ID"), List.of("单位名称"), List.of("应考人数"), List.of("实考人数"),
                        List.of("缺考人数"), List.of("参考率"), List.of("平均分"), List.of("及格率"),
                        List.of("同级排名"), List.of("等级分布"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) buildOrgSummary(session, root)
                        .get("children");
                for (Map<String, Object> c : children) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> gradeDist = (List<Map<String, Object>>) c.get("gradeDist");
                    String gradeText = gradeDist.stream()
                            .map(g -> g.get("gradeName") + ":" + g.get("count"))
                            .collect(Collectors.joining(";"));
                    rows.add(List.of(String.valueOf(c.get("orgId")), String.valueOf(c.get("orgName")),
                            String.valueOf(c.get("shouldCount")), String.valueOf(c.get("attendedCount")),
                            String.valueOf(c.get("absentCount")), String.valueOf(c.get("attendRate")),
                            c.get("avgScore") == null ? "" : c.get("avgScore").toString(),
                            String.valueOf(c.get("passRate")),
                            c.get("rank") == null ? "" : String.valueOf(c.get("rank")), gradeText));
                }
            }
            case "DETAIL" -> {
                head = List.of(List.of("用户ID"), List.of("姓名"), List.of("工号"), List.of("单位"),
                        List.of("成绩"), List.of("等级"), List.of("是否及格"), List.of("单位内排名"), List.of("答卷状态"));
                String pathPrefix;
                if (orgId != null) {
                    OrgUnit unit = resolveUnitInScope(orgId);
                    pathPrefix = unit.getPath();
                } else {
                    pathPrefix = SecurityUtils.dataScopePathPrefix();
                }
                List<ExamCandidate> candidates = scopedCandidates(examId, pathPrefix);
                Map<Long, AnswerSheet> sheetByUser = scopedSheets(examId, pathPrefix).stream()
                        .collect(Collectors.toMap(AnswerSheet::getUserId, Function.identity(), (a, b) -> a));
                BigDecimal passScore = passScoreOf(session);
                // 按名单快照单位分组计算单位内排名
                Map<Long, List<ExamCandidate>> byOrg = candidates.stream()
                        .collect(Collectors.groupingBy(ExamCandidate::getOrgId, LinkedHashMap::new,
                                Collectors.toList()));
                for (List<ExamCandidate> group : byOrg.values()) {
                    for (Map<String, Object> r : buildUserRows(group, sheetByUser, passScore)) {
                        rows.add(List.of(String.valueOf(r.get("userId")), String.valueOf(r.get("name")),
                                String.valueOf(r.get("jobNo")), String.valueOf(r.get("orgName")),
                                r.get("score") == null ? "" : r.get("score").toString(),
                                r.get("grade") == null ? "" : String.valueOf(r.get("grade")),
                                r.get("passed") == null ? "" : (Boolean.TRUE.equals(r.get("passed")) ? "是" : "否"),
                                r.get("rankInOrg") == null ? "" : String.valueOf(r.get("rankInOrg")),
                                sheetStatusText(r.get("sheetStatus"))));
                    }
                }
            }
            case "QUESTION" -> {
                head = List.of(List.of("序号"), List.of("题目ID"), List.of("题型"), List.of("题干"),
                        List.of("满分"), List.of("平均分"), List.of("答对率"), List.of("半对率"),
                        List.of("错误率"), List.of("得分率"), List.of("知识点标签"));
                for (Map<String, Object> q : questionAnalysis(examId, "SEQ", null)) {
                    rows.add(List.of(String.valueOf(q.get("seq")), String.valueOf(q.get("sheetQuesId")),
                            quesTypeCnName(q.get("quesType")),
                            q.get("stemText") == null ? "" : String.valueOf(q.get("stemText")),
                            q.get("fullScore") == null ? "" : q.get("fullScore").toString(),
                            q.get("avgScore") == null ? "" : q.get("avgScore").toString(),
                            String.valueOf(q.get("correctRate")), String.valueOf(q.get("halfRate")),
                            String.valueOf(q.get("wrongRate")),
                            q.get("scoreRate") == null ? "" : q.get("scoreRate").toString(),
                            ((List<?>) q.get("tags")).stream().map(String::valueOf)
                                    .collect(Collectors.joining(","))));
                }
            }
            default -> throw new BizException(ErrorCode.STAT_PARAM_ERROR,
                    "exportType 仅支持 SUMMARY/DETAIL/QUESTION");
        }
        try {
            EasyExcel.write(out).head(head).sheet("考试统计").doWrite(rows);
        } catch (Exception e) {
            throw new BizException(ErrorCode.EXPORT_FAILED, "导出失败: " + e.getMessage());
        }
        sysLogService.log(1, "STAT", "考试统计导出",
                "场次[" + session.getSessionName() + "] exportType=" + exportType
                        + "，orgId=" + orgId + "，行数=" + rows.size(), 1);
    }

    private static String sheetStatusText(Object sheetStatus) {
        if (sheetStatus == null) {
            return "未进入";
        }
        return switch (sheetStatus instanceof Number n ? n.intValue() : -1) {
            case 1 -> "答题中";
            case 2 -> "已交卷";
            default -> "";
        };
    }

    // ---------------------------------------------------------------- STAT-08 个人历次成绩单（考生端）

    public PageResult<Map<String, Object>> myTranscripts(long pageNum, long pageSize) {
        Long userId = SecurityUtils.getUserId();
        Page<AnswerSheet> page = answerSheetMapper.selectPage(new Page<>(pageNum, pageSize),
                new QueryWrapper<AnswerSheet>()
                        .eq("user_id", userId)
                        .eq("mark_status", MARK_PUBLISHED)
                        .orderByDesc("submit_time"));
        List<Long> sessionIds = page.getRecords().stream().map(AnswerSheet::getSessionId).distinct().toList();
        Map<Long, ExamSession> sessionMap = sessionIds.isEmpty() ? Map.of()
                : examSessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(ExamSession::getId, Function.identity()));
        boolean rankPublic = sysConfigService.getBool("stat.rankPublic", false);
        // 单位内排名缓存：sessionId:orgId → 已发布成绩排名键列表（分数降序、同分用时升序）
        Map<String, List<RankKey>> rankCache = new HashMap<>();

        return PageResult.of(page).map(sheet -> {
            ExamSession session = sessionMap.get(sheet.getSessionId());
            Map<String, Object> snapshot = sheet.getPaperSnapshot() == null ? Map.of() : sheet.getPaperSnapshot();
            BigDecimal fullScore = RandomStrategy.asBigDecimal(snapshot.get("totalScore"));
            BigDecimal passScore = RandomStrategy.asBigDecimal(snapshot.get("passScore"));
            BigDecimal myScore = sheet.getTotalScore();

            Integer rankInOrg = null;
            if (rankPublic && myScore != null) {
                String key = sheet.getSessionId() + ":" + sheet.getOrgId();
                List<RankKey> keys = rankCache.computeIfAbsent(key, k ->
                        answerSheetMapper.selectList(new QueryWrapper<AnswerSheet>()
                                        .select("id", "total_score", "duration_sec")
                                        .eq("session_id", sheet.getSessionId())
                                        .eq("org_id", sheet.getOrgId())
                                        .eq("mark_status", MARK_PUBLISHED)
                                        .isNotNull("total_score"))
                                .stream().map(s -> new RankKey(s.getTotalScore(), s.getDurationSec()))
                                .sorted(RANK_ORDER).toList());
                rankInOrg = rankOf(keys, new RankKey(myScore, sheet.getDurationSec()));
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("examId", sheet.getSessionId());
            row.put("examName", session == null ? null : session.getSessionName());
            row.put("examTime", session == null || session.getStartTime() == null
                    ? null : session.getStartTime().format(DT));
            row.put("myScore", myScore);
            row.put("totalScore", fullScore);
            row.put("grade", sheet.getGradeName());
            row.put("passed", myScore == null || passScore == null ? null : myScore.compareTo(passScore) >= 0);
            row.put("durationSec", sheet.getDurationSec());
            row.put("rankInOrg", rankInOrg);
            row.put("retakeSeq", session != null && session.getSessionKind() != null
                    && session.getSessionKind() == 2 ? 1 : 0);
            return row;
        });
    }

    // ---------------------------------------------------------------- 数值工具

    /** 0~1 比率（scale 4），分母为 0 返回 0 */
    private static BigDecimal ratio(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal avg(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static long nvl(Integer v) {
        return v == null ? 0L : v;
    }
}
