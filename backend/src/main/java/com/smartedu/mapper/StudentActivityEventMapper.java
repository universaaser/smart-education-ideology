package com.smartedu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartedu.entity.StudentActivityEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentActivityEventMapper extends BaseMapper<StudentActivityEvent> {

    @Select("""
            <script>
            SELECT COALESCE(SUM(duration_seconds), 0)
            FROM student_activity_events
            WHERE student_id = #{studentId}
            <if test="courseId != null">
                AND course_id = #{courseId}
            </if>
            </script>
            """)
    Long sumDurationSeconds(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId);
}
