package com.smartedu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartedu.entity.CourseKnowledgePoint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程知识点关联 Mapper
 */
@Mapper
public interface CourseKnowledgePointMapper extends BaseMapper<CourseKnowledgePoint> {
}
