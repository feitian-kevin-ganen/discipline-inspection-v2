package com.group.exam.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.group.exam.system.entity.SysNotice;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 管理端通知分页：同一批次（同类型/标题/内容/业务/操作人/同事务时间）聚合为一行，
     * 统计发送数与已读数。
     */
    @Select({
            "<script>",
            "SELECT MIN(id) AS id, notice_type, title, biz_type, biz_id,",
            "COUNT(*) AS sent_count, COALESCE(SUM(read_status), 0) AS read_count,",
            "MIN(create_by) AS create_by, MIN(create_time) AS create_time",
            "FROM t_sys_notice",
            "<where>",
            "<if test='noticeType != null'>notice_type = #{noticeType}</if>",
            "</where>",
            "GROUP BY notice_type, title, content, biz_type, biz_id, create_by, create_time",
            "ORDER BY MIN(create_time) DESC",
            "</script>"
    })
    IPage<Map<String, Object>> pageGrouped(Page<Map<String, Object>> page, @Param("noticeType") Integer noticeType);
}
