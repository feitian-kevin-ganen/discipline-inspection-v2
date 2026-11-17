package com.group.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.exam.entity.MarkTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MarkTaskMapper extends BaseMapper<MarkTask> {

    /** 乐观锁领取：仅待阅任务可领取，返回影响行数（0 表示已被领取/已阅） */
    @Update("UPDATE t_mark_task SET marker_id = #{markerId}, marker_name = #{markerName}, assign_time = now() "
            + "WHERE id = #{taskId} AND mark_status = 0")
    int claim(@Param("taskId") Long taskId, @Param("markerId") Long markerId, @Param("markerName") String markerName);
}
