package com.group.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.exam.entity.ExamCandidate;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ExamCandidateMapper extends BaseMapper<ExamCandidate> {

    /** 含已剔除（deleted=1）的名单行查询，用于追加时复活而非重复插入（uk(session_id,user_id) 兜底） */
    @Select("SELECT * FROM t_exam_candidate WHERE session_id = #{sessionId} AND user_id = #{userId}")
    ExamCandidate selectIncludeDeleted(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    /** 名单全量重建：物理清空（已在考/已交卷场次禁止重建，由 Service 校验） */
    @Delete("DELETE FROM t_exam_candidate WHERE session_id = #{sessionId}")
    int physicalDeleteBySessionId(@Param("sessionId") Long sessionId);

    /** 复活被剔除的名单行并刷新快照（显式 SQL 绕过逻辑删除更新限制） */
    @Update("UPDATE t_exam_candidate SET deleted = 0, user_name = #{c.userName}, job_no = #{c.jobNo}, "
            + "org_id = #{c.orgId}, org_path = #{c.orgPath}, org_name = #{c.orgName}, candidate_status = 0, "
            + "source = #{c.source}, notify_status = 0, update_by = #{c.updateBy} "
            + "WHERE id = #{c.id}")
    int revive(@Param("c") ExamCandidate candidate);
}
