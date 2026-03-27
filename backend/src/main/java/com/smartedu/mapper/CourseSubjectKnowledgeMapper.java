package com.smartedu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartedu.entity.CourseSubjectKnowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程与学科知识关联 Mapper
 */
@Mapper
public interface CourseSubjectKnowledgeMapper extends BaseMapper<CourseSubjectKnowledge> {
}
