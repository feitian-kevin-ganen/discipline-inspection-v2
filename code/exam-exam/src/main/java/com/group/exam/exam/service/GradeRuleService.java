package com.group.exam.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.exam.dto.GradeRuleItemReq;
import com.group.exam.exam.entity.GradeRule;
import com.group.exam.exam.entity.GradeRuleItem;
import com.group.exam.exam.mapper.GradeRuleItemMapper;
import com.group.exam.exam.mapper.GradeRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 等级规则（t_grade_rule + t_grade_rule_item）：区间校验、保存、快照、命中评定
 */
@Service
public class GradeRuleService extends ServiceImpl<GradeRuleMapper, GradeRule> {

    private final GradeRuleItemMapper gradeRuleItemMapper;

    public GradeRuleService(GradeRuleItemMapper gradeRuleItemMapper) {
        this.gradeRuleItemMapper = gradeRuleItemMapper;
    }

    /**
     * 校验区间连续且完整覆盖 [0, fullScore]：按 minScore 升序，首段 min=0、末段 max=fullScore、
     * 相邻段边界衔接（prev.max == next.min），否则 40005
     */
    public void validateItems(List<GradeRuleItemReq> items, BigDecimal fullScore) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "等级规则区间不能为空");
        }
        if (fullScore == null || fullScore.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "卷面总分必须为正数");
        }
        List<GradeRuleItemReq> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(GradeRuleItemReq::getMinScore,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        for (GradeRuleItemReq item : sorted) {
            if (item.getGradeName() == null || item.getGradeName().isBlank()
                    || item.getMinScore() == null || item.getMaxScore() == null
                    || item.getMinScore().compareTo(item.getMaxScore()) > 0) {
                throw new BizException(ErrorCode.GRADE_RULE_INVALID, "等级区间非法：每段需含 gradeName/minScore/maxScore 且 minScore<=maxScore");
            }
        }
        if (sorted.get(0).getMinScore().compareTo(BigDecimal.ZERO) != 0) {
            throw new BizException(ErrorCode.GRADE_RULE_INVALID, "等级区间未从 0 分开始覆盖");
        }
        if (sorted.get(sorted.size() - 1).getMaxScore().compareTo(fullScore) != 0) {
            throw new BizException(ErrorCode.GRADE_RULE_INVALID, "等级区间未完整覆盖至满分 " + fullScore);
        }
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).getMaxScore().compareTo(sorted.get(i).getMinScore()) != 0) {
                throw new BizException(ErrorCode.GRADE_RULE_INVALID, "等级区间不连续或存在重叠");
            }
        }
    }

    /**
     * 保存自定义等级规则（新建规则 + 区间项，左闭右开、末段右闭），返回规则 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(String ruleName, BigDecimal fullScore, List<GradeRuleItemReq> items) {
        validateItems(items, fullScore);
        List<GradeRuleItemReq> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(GradeRuleItemReq::getMinScore));

        GradeRule rule = new GradeRule();
        rule.setRuleName(ruleName);
        rule.setFullScore(fullScore);
        rule.setIsDefault(0);
        rule.setStatus(1);
        rule.setCreateBy(currentUserIdOrNull());
        save(rule);

        for (int i = 0; i < sorted.size(); i++) {
            GradeRuleItemReq req = sorted.get(i);
            GradeRuleItem item = new GradeRuleItem();
            item.setRuleId(rule.getId());
            item.setGradeCode("G" + (i + 1));
            item.setGradeName(req.getGradeName().trim());
            item.setMinScore(req.getMinScore());
            item.setMaxScore(req.getMaxScore());
            item.setLowerInclusive(1);
            item.setUpperInclusive(i == sorted.size() - 1 ? 1 : 0);
            item.setSortNo(i + 1);
            item.setCreateBy(rule.getCreateBy());
            gradeRuleItemMapper.insert(item);
        }
        return rule.getId();
    }

    public List<GradeRuleItem> listItems(Long ruleId) {
        if (ruleId == null) {
            return List.of();
        }
        return gradeRuleItemMapper.selectList(
                new QueryWrapper<GradeRuleItem>().eq("rule_id", ruleId).orderByAsc("sort_no"));
    }

    /** 系统默认等级规则 id（is_default=1），无则 null */
    public Long defaultRuleId() {
        GradeRule rule = getOne(new QueryWrapper<GradeRule>().eq("is_default", 1).eq("status", 1), false);
        return rule == null ? null : rule.getId();
    }

    /** 规则条目快照（嵌入试卷/场次/答卷快照）：[{gradeCode,gradeName,minScore,maxScore,lowerInclusive,upperInclusive}] */
    public List<Map<String, Object>> snapshotItems(Long ruleId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (GradeRuleItem item : listItems(ruleId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gradeCode", item.getGradeCode());
            m.put("gradeName", item.getGradeName());
            m.put("minScore", item.getMinScore());
            m.put("maxScore", item.getMaxScore());
            m.put("lowerInclusive", item.getLowerInclusive());
            m.put("upperInclusive", item.getUpperInclusive());
            list.add(m);
        }
        return list;
    }

    /** 按快照区间命中等级，返回该区间快照 Map，未命中返回 null */
    public Map<String, Object> resolveGrade(List<Map<String, Object>> items, BigDecimal score) {
        if (items == null || score == null) {
            return null;
        }
        for (Map<String, Object> item : items) {
            BigDecimal min = toBigDecimal(item.get("minScore"));
            BigDecimal max = toBigDecimal(item.get("maxScore"));
            if (min == null || max == null) {
                continue;
            }
            boolean lowerOk = toInt(item.get("lowerInclusive"), 1) == 1
                    ? score.compareTo(min) >= 0 : score.compareTo(min) > 0;
            boolean upperOk = toInt(item.get("upperInclusive"), 0) == 1
                    ? score.compareTo(max) <= 0 : score.compareTo(max) < 0;
            if (lowerOk && upperOk) {
                return item;
            }
        }
        return null;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
