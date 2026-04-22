package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.CourseCreateRequestDto;
import com.smartedu.dto.CourseTeachingMaterialGroupDto;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.entity.Course;
import com.smartedu.service.CourseService;
import com.smartedu.service.TeachingMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Course controller.
 *
 * <p>
 * Handles course CRUD plus linked knowledge/material queries.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final TeachingMaterialService teachingMaterialService;

    /**
     * Query linked knowledge points for one course.
     */
    @GetMapping("/{id}/knowledge-points")
    public Result<List<KnowledgeNodeView>> getCourseKnowledgePoints(@PathVariable Long id) {
        List<KnowledgeNodeView> points = courseService.getCourseKnowledgePoints(id);
        return Result.success(points);
    }

    /**
     * Query saved teaching materials grouped by upload task under one course.
     */
    @GetMapping("/{id}/materials")
    public Result<List<CourseTeachingMaterialGroupDto>> getCourseTeachingMaterials(@PathVariable Long id) {
        // Keep this read path direct so CourseService no longer depends on
        // TeachingMaterialService and startup stays free of circular references.
        List<CourseTeachingMaterialGroupDto> materials = teachingMaterialService.getCourseMaterialGroups(id);
        return Result.success(materials);
    }

    /**
     * Create a new course.
     */
    @PostMapping
    public Result<Course> createCourse(@RequestBody CourseCreateRequestDto request) {
        String courseName = request == null || request.getName() == null ? "" : request.getName().trim();
        if (courseName.isEmpty()) {
            return Result.badRequest("Course name cannot be empty");
        }
        // Keep course ownership explicit instead of silently attaching new courses to a demo teacher.
        if (request == null || request.getTeacherId() == null) {
            return Result.badRequest("Teacher id cannot be empty");
        }

        Course course = courseService.createCourse(
                courseName,
                request.getCode(),
                request.getDescription(),
                request.getSemester(),
                request.getTeacherId());
        return Result.success("Course created", course);
    }
}
