package com.group.exam.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.question.dto.BankSaveReq;
import com.group.exam.question.dto.BankTreeVO;
import com.group.exam.question.entity.QuesBank;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.mapper.QuesBankMapper;
import com.group.exam.question.mapper.QuesItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题库分类树（QUES-01~04）
 */
@Service
public class QuesBankService extends ServiceImpl<QuesBankMapper, QuesBank> {

    private final QuesItemMapper quesItemMapper;

    public QuesBankService(QuesItemMapper quesItemMapper) {
        this.quesItemMapper = quesItemMapper;
    }

    /**
     * 分类树（QUES-01）：含各分类题目数（不含下级）
     */
    public List<BankTreeVO> tree() {
        List<QuesBank> banks = list(new QueryWrapper<QuesBank>().orderByAsc("sort_no", "id"));
        Map<Long, Long> itemCounts = countItemsGroupByBank();
        Map<Long, BankTreeVO> nodeMap = new LinkedHashMap<>();
        for (QuesBank bank : banks) {
            BankTreeVO node = new BankTreeVO();
            node.setBankId(bank.getId());
            node.setParentId(bank.getParentId() == null ? 0L : bank.getParentId());
            node.setBankName(bank.getBankName());
            node.setSortNo(bank.getSortNo());
            node.setItemCount(itemCounts.getOrDefault(bank.getId(), 0L));
            node.setChildren(new ArrayList<>());
            nodeMap.put(bank.getId(), node);
        }
        List<BankTreeVO> roots = new ArrayList<>();
        for (BankTreeVO node : nodeMap.values()) {
            BankTreeVO parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 新增分类（QUES-02）：path=父path+id+"/"，level=父level+1
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(BankSaveReq req) {
        QuesBank bank = new QuesBank();
        bank.setBankName(req.getBankName().trim());
        bank.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        bank.setStatus(1);
        bank.setCreateBy(currentUserIdOrNull());

        Long parentId = req.getParentId() == null ? 0L : req.getParentId();
        String parentPath = "/";
        if (parentId == 0L) {
            bank.setParentId(null);
            bank.setLevel(1);
            bank.setPath("");
        } else {
            QuesBank parent = getById(parentId);
            if (parent == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "上级题库分类不存在: " + parentId);
            }
            bank.setParentId(parentId);
            bank.setLevel(parent.getLevel() + 1);
            bank.setPath("");
            parentPath = parent.getPath();
        }
        save(bank);
        bank.setPath(parentPath + bank.getId() + "/");
        updateById(bank);
        return bank.getId();
    }

    /**
     * 编辑分类（QUES-03）：仅名称与排序，不改层级
     */
    public void update(Long bankId, BankSaveReq req) {
        QuesBank bank = getById(bankId);
        if (bank == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题库分类不存在: " + bankId);
        }
        bank.setBankName(req.getBankName().trim());
        if (req.getSortNo() != null) {
            bank.setSortNo(req.getSortNo());
        }
        bank.setUpdateBy(currentUserIdOrNull());
        updateById(bank);
    }

    /**
     * 删除分类（QUES-04）：存在子分类或（含下级）题目时报 20004
     */
    public void delete(Long bankId) {
        QuesBank bank = getById(bankId);
        if (bank == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题库分类不存在: " + bankId);
        }
        long childCount = count(new QueryWrapper<QuesBank>().eq("parent_id", bankId));
        if (childCount > 0) {
            throw new BizException(ErrorCode.UNIT_HAS_CHILDREN, "存在子分类，不可删除");
        }
        long itemCount = quesItemMapper.selectCount(new QueryWrapper<QuesItem>()
                .inSql("bank_id", "SELECT id FROM t_ques_bank WHERE deleted = 0 AND path LIKE '" + bank.getPath() + "%'"));
        if (itemCount > 0) {
            throw new BizException(ErrorCode.UNIT_HAS_CHILDREN, "分类（含下级）下存在题目，不可删除");
        }
        removeById(bankId);
    }

    /**
     * 展开分类 ID 集合：includeChildren=true 时按物化路径含全部下级
     */
    public Set<Long> expandBankIds(List<Long> bankIds, boolean includeChildren) {
        if (bankIds == null || bankIds.isEmpty()) {
            return Set.of();
        }
        if (!includeChildren) {
            return new LinkedHashSet<>(bankIds);
        }
        List<QuesBank> roots = list(new QueryWrapper<QuesBank>()
                .select("id", "path")
                .in("id", bankIds));
        if (roots.isEmpty()) {
            return Set.of();
        }
        QueryWrapper<QuesBank> qw = new QueryWrapper<QuesBank>().select("id");
        qw.and(w -> {
            for (QuesBank root : roots) {
                w.or().likeRight("path", root.getPath());
            }
        });
        return list(qw).stream().map(QuesBank::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 各分类未删除题目数（group by bank_id） */
    private Map<Long, Long> countItemsGroupByBank() {
        List<Map<String, Object>> rows = quesItemMapper.selectMaps(new QueryWrapper<QuesItem>()
                .select("bank_id", "COUNT(*) AS cnt")
                .groupBy("bank_id"));
        Map<Long, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object bankId = row.get("bank_id");
            Object cnt = row.get("cnt");
            if (bankId != null && cnt != null) {
                map.put(Long.parseLong(bankId.toString()), Long.parseLong(cnt.toString()));
            }
        }
        return map;
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
