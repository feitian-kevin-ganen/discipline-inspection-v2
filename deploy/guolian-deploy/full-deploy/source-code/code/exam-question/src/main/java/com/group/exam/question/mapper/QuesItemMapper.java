package com.group.exam.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.question.entity.QuesItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface QuesItemMapper extends BaseMapper<QuesItem> {

    /** 题目被试卷引用次数（t_paper_question，物理删除前置校验） */
    @Select("SELECT COUNT(*) FROM t_paper_question WHERE ques_id = #{quesId}")
    long countPaperReferences(@Param("quesId") Long quesId);

    /** 物理删除（QUES-09 hard=true，逻辑删除之外的真删除） */
    @Delete("DELETE FROM t_ques_item WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
