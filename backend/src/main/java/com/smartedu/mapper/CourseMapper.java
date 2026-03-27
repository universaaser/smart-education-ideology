package com.smartedu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartedu.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程 Mapper 接口
 * 
 * @author SmartEducation Team
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
