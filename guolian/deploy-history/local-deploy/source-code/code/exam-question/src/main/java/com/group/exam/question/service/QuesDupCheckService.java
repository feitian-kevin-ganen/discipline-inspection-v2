package com.group.exam.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.question.dto.DupHintVO;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.ItemStatusEnum;
import com.group.exam.question.mapper.QuesItemMapper;
import com.group.exam.question.support.StemTextUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 题干查重（QUES-12，QUES-06 保存时自动执行）。
 *
 * <p>先按规范化 stem_md5 精确命中（similarity=1.0）；再做相似度：
 * 题干纯文本字符 bigram 集合 Jaccard 相似度，≥阈值（ques.dup.threshold，默认 0.8）
 * 视为疑似重复，按相似度降序返回前 10 条。纯检测，不入库。
 */
@Service
public class QuesDupCheckService extends ServiceImpl<QuesItemMapper, QuesItem> {

    /** 相似度候选集上限（同题型最近 N 条） */
    private static final int CANDIDATE_LIMIT = 2000;

    /** 返回条数上限 */
    private static final int RESULT_LIMIT = 10;

    private final SysConfigService sysConfigService;

    public QuesDupCheckService(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 查重。
     *
     * @param stem          题干（富文本 HTML 或纯文本）
     * @param quesType      题型 DB 编码（1-5），null 不限
     * @param excludeItemId 排除的题目 ID（编辑场景自查），null 不排除
     */
    public List<DupHintVO> dupCheck(String stem, Integer quesType, Long excludeItemId) {
        String md5 = normalizeMd5(stem);
        String normalized = StemTextUtils.normalize(stem);
        if (normalized.isEmpty()) {
            return List.of();
        }
        double threshold = sysConfigService.getDouble(SysConfigService.KEY_DUP_THRESHOLD, 0.8d);

        // 1. stem_md5 精确命中（停用/已删除除外）
        QueryWrapper<QuesItem> exactQw = new QueryWrapper<QuesItem>()
                .eq("stem_md5", md5)
                .ne("status", ItemStatusEnum.DISABLED.getCode())
                .ne(excludeItemId != null, "id", excludeItemId);
        List<QuesItem> exactHits = list(exactQw);

        List<DupHintVO> result = new ArrayList<>();
        Set<Long> hitIds = new HashSet<>();
        for (QuesItem hit : exactHits) {
            hitIds.add(hit.getId());
            result.add(toHint(hit, 1.0d));
        }

        // 2. bigram Jaccard 相似度（同题型、待审/启用，最近 CANDIDATE_LIMIT 条候选）
        QueryWrapper<QuesItem> candQw = new QueryWrapper<QuesItem>()
                .select("id", "stem", "status")
                .eq(quesType != null, "ques_type", quesType)
                .in("status", ItemStatusEnum.PENDING_AUDIT.getCode(), ItemStatusEnum.ENABLED.getCode())
                .ne(excludeItemId != null, "id", excludeItemId)
                .orderByDesc("id")
                .last("LIMIT " + CANDIDATE_LIMIT);
        for (QuesItem candidate : list(candQw)) {
            if (hitIds.contains(candidate.getId())) {
                continue;
            }
            double similarity = StemTextUtils.bigramJaccard(normalized, StemTextUtils.normalize(candidate.getStem()));
            if (similarity >= threshold) {
                result.add(toHint(candidate, similarity));
            }
        }

        result.sort(Comparator.comparing(DupHintVO::getSimilarity).reversed());
        return result.size() > RESULT_LIMIT ? new ArrayList<>(result.subList(0, RESULT_LIMIT)) : result;
    }

    /** 题干规范化 MD5（去 HTML 标签、去空白、全角转半角、转小写） */
    public String normalizeMd5(String stemHtml) {
        return StemTextUtils.normalizeMd5(stemHtml);
    }

    private DupHintVO toHint(QuesItem item, double similarity) {
        DupHintVO vo = new DupHintVO();
        vo.setItemId(item.getId());
        vo.setSimilarity(BigDecimal.valueOf(similarity).setScale(4, RoundingMode.HALF_UP).doubleValue());
        vo.setStemText(StemTextUtils.summary(item.getStem(), 100));
        vo.setStatus(ItemStatusEnum.toApi(item.getStatus()));
        return vo;
    }
}
