package com.group.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.exam.entity.PaperQuestion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface PaperQuestionMapper extends BaseMapper<PaperQuestion> {

    /** 全量替换选题前物理清空（t_paper_question 无 deleted 字段） */
    @Delete("DELETE FROM t_paper_question WHERE paper_id = #{paperId}")
    int physicalDeleteByPaperId(@Param("paperId") Long paperId);
}
