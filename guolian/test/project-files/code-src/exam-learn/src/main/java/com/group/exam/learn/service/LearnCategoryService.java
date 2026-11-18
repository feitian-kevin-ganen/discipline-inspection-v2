package com.group.exam.learn.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.learn.dto.CategoryCreateRequest;
import com.group.exam.learn.dto.CategoryUpdateRequest;
import com.group.exam.learn.entity.LearnCategory;
import com.group.exam.learn.entity.LearnMaterial;
import com.group.exam.learn.mapper.LearnCategoryMapper;
import com.group.exam.learn.mapper.LearnMaterialMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资料分类树服务（LIB-01~04）
 */
@Service
public class LearnCategoryService extends ServiceImpl<LearnCategoryMapper, LearnCategory> {

    private final LearnMaterialMapper learnMaterialMapper;
    private final LearnScopeChecker scopeChecker;

    public LearnCategoryService(LearnMaterialMapper learnMaterialMapper, LearnScopeChecker scopeChecker) {
        this.learnMaterialMapper = learnMaterialMapper;
        this.scopeChecker = scopeChecker;
    }

    /** LIB-01 分类树：[{catId, parentId, catName, sortNo, scopeType, children[]}] */
    public List<Map<String, Object>> tree() {
        List<LearnCategory> all = list(new QueryWrapper<LearnCategory>()
                .orderByAsc("level").orderByAsc("sort_no").orderByAsc("id"));
        Map<Long, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (LearnCategory cat : all) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("catId", cat.getId());
            node.put("parentId", cat.getParentId() == null ? 0 : cat.getParentId());
            node.put("catName", cat.getCategoryName());
            node.put("sortNo", cat.getSortNo());
            node.put("scopeType", scopeChecker.scopeTypeOf(cat.getVisibleScope()));
            node.put("scopeOrgIds", scopeChecker.scopeOrgIdsOf(cat.getVisibleScope()));
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(cat.getId(), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (LearnCategory cat : all) {
            Map<String, Object> node = nodeMap.get(cat.getId());
            Long parentId = cat.getParentId();
            if (parentId == null || parentId == 0 || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children =
                        (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(node);
            }
        }
        return roots;
    }

    /** LIB-02 新增分类 */
    public Long create(CategoryCreateRequest req) {
        LearnCategory cat = new LearnCategory();
        long parentId = req.getParentId() == null ? 0 : req.getParentId();
        if (parentId > 0) {
            LearnCategory parent = getById(parentId);
            if (parent == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "上级分类不存在");
            }
            cat.setParentId(parentId);
            cat.setLevel(parent.getLevel() + 1);
        } else {
            cat.setParentId(0L);
            cat.setLevel(1);
        }
        cat.setCategoryName(req.getCatName());
        cat.setSortNo(req.getSortNo() != null ? req.getSortNo() : 0);
        cat.setVisibleScope(Map.of("type", LearnScopeChecker.TYPE_ALL));
        cat.setStatus(1);
        cat.setCreateBy(SecurityUtils.getUserId());
        save(cat);
        return cat.getId();
    }

    /** LIB-03 编辑分类（含可见范围） */
    public void update(Long catId, CategoryUpdateRequest req) {
        LearnCategory cat = requireCategory(catId);
        if (StringUtils.hasText(req.getCatName())) {
            cat.setCategoryName(req.getCatName());
        }
        if (req.getSortNo() != null) {
            cat.setSortNo(req.getSortNo());
        }
        if (StringUtils.hasText(req.getScopeType())) {
            cat.setVisibleScope(scopeChecker.buildScope(req.getScopeType(), req.getScopeOrgIds()));
        }
        cat.setUpdateBy(SecurityUtils.getUserId());
        updateById(cat);
    }

    /** LIB-04 删除分类（须无子分类且无资料） */
    public void delete(Long catId) {
        requireCategory(catId);
        long children = count(new QueryWrapper<LearnCategory>().eq("parent_id", catId));
        if (children > 0) {
            throw new BizException(ErrorCode.UNIT_HAS_CHILDREN, "分类下存在子分类，不可删除");
        }
        Long materials = learnMaterialMapper.selectCount(new QueryWrapper<LearnMaterial>().eq("category_id", catId));
        if (materials != null && materials > 0) {
            throw new BizException(ErrorCode.UNIT_HAS_CHILDREN, "分类下存在资料，不可删除");
        }
        removeById(catId);
    }

    public LearnCategory requireCategory(Long catId) {
        LearnCategory cat = catId == null ? null : getById(catId);
        if (cat == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return cat;
    }

    /** 含下级的分类id集合（t_learn_category 无物化路径，按 parent_id 递归收集） */
    public List<Long> collectSubIds(Long rootCatId) {
        List<LearnCategory> all = list(new QueryWrapper<LearnCategory>()
                .select("id", "parent_id"));
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (LearnCategory cat : all) {
            childrenMap.computeIfAbsent(cat.getParentId() == null ? 0L : cat.getParentId(),
                    k -> new ArrayList<>()).add(cat.getId());
        }
        List<Long> result = new ArrayList<>();
        collect(rootCatId, childrenMap, result);
        return result;
    }

    private void collect(Long id, Map<Long, List<Long>> childrenMap, List<Long> result) {
        result.add(id);
        for (Long child : childrenMap.getOrDefault(id, List.of())) {
            collect(child, childrenMap, result);
        }
    }

    /** catId → 分类名 */
    public Map<Long, String> mapCatNames(Collection<Long> catIds) {
        Map<Long, String> result = new HashMap<>();
        if (catIds == null || catIds.isEmpty()) {
            return result;
        }
        for (LearnCategory cat : list(new QueryWrapper<LearnCategory>().in("id", catIds))) {
            result.put(cat.getId(), cat.getCategoryName());
        }
        return result;
    }
}
