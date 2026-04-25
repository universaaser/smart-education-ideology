package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.StudentEventBatchRequestDto;
import com.smartedu.dto.StudentLearningReportDto;
import com.smartedu.dto.StudentRecentActivityDto;
import com.smartedu.service.StudentActivityEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentActivityEventController {

    private final StudentActivityEventService studentActivityEventService;

    @PostMapping("/events")
    public Result<Map<String, Object>> submitEvents(@RequestBody StudentEventBatchRequestDto request) {
        if (request == null || request.getStudentId() == null) {
            return Result.badRequest("Student id cannot be empty");
        }
        if (request.getCourseId() == null) {
            return Result.badRequest("Course id cannot be empty");
        }
        if (request.getEvents() == null || request.getEvents().isEmpty()) {
            return Result.badRequest("Events cannot be empty");
        }
        int savedCount = studentActivityEventService.saveBatch(request);
        return Result.success(Map.of("savedCount", savedCount));
    }

    @GetMapping("/report")
    public Result<StudentLearningReportDto> getReport(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long courseId) {
        if (studentId == null) {
            return Result.badRequest("Student id cannot be empty");
        }
        return Result.success(studentActivityEventService.getReport(studentId, courseId));
    }

    @GetMapping("/recent-activities")
    public Result<List<StudentRecentActivityDto>> getRecentActivities(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "8") int limit) {
        if (studentId == null) {
            return Result.badRequest("Student id cannot be empty");
        }
        return Result.success(studentActivityEventService.getRecentActivities(studentId, courseId, limit));
    }
}
