package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.entity.Course;
import com.smartedu.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程控制器
 * 
 * <p>
 * 处理课程 CRUD 和课程关联知识点查询
 * 
 * @author SmartEducation Team
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * 获取课程关联的知识点列表
     * 
     * @param id 课程ID
     * @return 知识点列表（包含技术定义和思政价值）
     */
    @GetMapping("/{id}/knowledge-points")
    public Result<List<KnowledgeNodeView>> getCourseKnowledgePoints(@PathVariable Long id) {
        List<KnowledgeNodeView> points = courseService.getCourseKnowledgePoints(id);
        return Result.success(points);
    }

    /**
     * 创建新课程
     */
    @PostMapping
    public Result<Course> createCourse(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String code = (String) request.get("code");
        String description = (String) request.get("description");
        String semester = (String) request.get("semester");
        Long teacherId = request.get("teacherId") != null
                ? Long.valueOf(request.get("teacherId").toString())
                : 1L;

        if (name == null || name.isEmpty()) {
            return Result.badRequest("课程名称不能为空");
        }

        Course course = courseService.createCourse(name, code, description, semester, teacherId);
        return Result.success("课程创建成功", course);
    }
}
