package com.group.exam.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.group.exam.practice.entity.PracticeRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 练习记录 Mapper（t_practice_record）
 */
@Mapper
public interface PracticeRecordMapper extends BaseMapper<PracticeRecord> {
}
