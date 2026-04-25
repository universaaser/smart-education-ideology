package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.CourseChapterDto;
import com.smartedu.dto.CourseChapterRequestDto;
import com.smartedu.dto.CourseCreateRequestDto;
import com.smartedu.dto.CourseStatusSummaryDto;
import com.smartedu.dto.CourseStudentDto;
import com.smartedu.dto.CourseStudentRequestDto;
import com.smartedu.dto.CourseTeachingMaterialGroupDto;
import com.smartedu.dto.CourseUpdateRequestDto;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.dto.StudentCourseDto;
import com.smartedu.dto.StudentOptionDto;
import com.smartedu.entity.Course;
import com.smartedu.service.CourseService;
import com.smartedu.service.CourseStudentService;
import com.smartedu.service.TeachingMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
    private final CourseStudentService courseStudentService;

    @GetMapping("/student/{studentId}")
    public Result<List<StudentCourseDto>> getStudentCourses(@PathVariable Long studentId) {
        return Result.success(courseStudentService.listStudentCourses(studentId));
    }

    @GetMapping("/student-options")
    public Result<List<StudentOptionDto>> listStudentOptions() {
        return Result.success(courseStudentService.listStudentOptions());
    }

    /**
     * Query linked knowledge points for one course.
     */
    @GetMapping("/{id}")
    public Result<Course> getCourse(@PathVariable Long id) {
        Course course = courseService.getCourseById(id);
        if (course == null) {
            return Result.notFound("Course not found");
        }
        return Result.success(course);
    }

    @PutMapping("/{id}")
    public Result<Course> updateCourse(@PathVariable Long id, @RequestBody CourseUpdateRequestDto request) {
        if (request == null) {
            return Result.badRequest("Request body cannot be empty");
        }
        if (request.getName() != null && request.getName().trim().isEmpty()) {
            return Result.badRequest("Course name cannot be empty");
        }
        Course course;
        try {
            course = courseService.updateCourse(id, request);
        } catch (IllegalArgumentException ex) {
            return Result.badRequest(ex.getMessage());
        }
        if (course == null) {
            return Result.notFound("Course not found");
        }
        return Result.success(course);
    }

    @GetMapping("/{id}/knowledge-points")
    public Result<List<KnowledgeNodeView>> getCourseKnowledgePoints(@PathVariable Long id) {
        List<KnowledgeNodeView> points = courseService.getCourseKnowledgePoints(id);
        return Result.success(points);
    }

    @GetMapping("/{id}/chapters")
    public Result<List<CourseChapterDto>> getCourseChapters(@PathVariable Long id) {
        return Result.success(courseService.getCourseChapters(id));
    }

    @PostMapping("/{id}/chapters")
    public Result<CourseChapterDto> createCourseChapter(
            @PathVariable Long id,
            @RequestBody CourseChapterRequestDto request) {
        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            return Result.badRequest("Chapter title cannot be empty");
        }
        return Result.success(courseService.createCourseChapter(id, request));
    }

    @PutMapping("/{id}/chapters/{chapterId}")
    public Result<CourseChapterDto> updateCourseChapter(
            @PathVariable Long id,
            @PathVariable Long chapterId,
            @RequestBody CourseChapterRequestDto request) {
        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            return Result.badRequest("Chapter title cannot be empty");
        }
        CourseChapterDto chapter = courseService.updateCourseChapter(id, chapterId, request);
        if (chapter == null) {
            return Result.badRequest("Invalid course chapter");
        }
        return Result.success(chapter);
    }

    @PostMapping("/{id}/chapters/{chapterId}/materials/{materialId}")
    public Result<Map<String, Object>> bindMaterialToChapter(
            @PathVariable Long id,
            @PathVariable Long chapterId,
            @PathVariable Long materialId) {
        boolean bound = courseService.bindMaterialToChapter(id, chapterId, materialId);
        if (!bound) {
            return Result.badRequest("Invalid chapter or material");
        }
        return Result.success(Map.of("bound", true));
    }

    @GetMapping("/{id}/status-summary")
    public Result<CourseStatusSummaryDto> getCourseStatusSummary(@PathVariable Long id) {
        return Result.success(courseService.getCourseStatusSummary(id));
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

    @GetMapping("/{id}/students")
    public Result<List<CourseStudentDto>> listCourseStudents(@PathVariable Long id) {
        return Result.success(courseStudentService.listCourseStudents(id));
    }

    @PostMapping("/{id}/students")
    public Result<CourseStudentDto> addCourseStudent(@PathVariable Long id, @RequestBody CourseStudentRequestDto request) {
        if (request == null || request.getStudentId() == null) {
            return Result.badRequest("Student id cannot be empty");
        }
        CourseStudentDto student = courseStudentService.addStudent(id, request.getStudentId());
        if (student == null) {
            return Result.badRequest("Invalid course or student");
        }
        return Result.success(student);
    }

    @DeleteMapping("/{id}/students/{studentId}")
    public Result<Void> removeCourseStudent(@PathVariable Long id, @PathVariable Long studentId) {
        courseStudentService.removeStudent(id, studentId);
        return Result.success();
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
