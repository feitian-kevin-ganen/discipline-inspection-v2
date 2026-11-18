package com.group.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.exam.entity.GradeRuleItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface GradeRuleItemMapper extends BaseMapper<GradeRuleItem> {

    /** t_grade_rule_item 无 deleted 字段，规则重建时物理删除 */
    @Delete("DELETE FROM t_grade_rule_item WHERE rule_id = #{ruleId}")
    int physicalDeleteByRuleId(@Param("ruleId") Long ruleId);
}
