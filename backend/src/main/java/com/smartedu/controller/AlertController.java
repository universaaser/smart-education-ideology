package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.StudentAlertRecordDto;
import com.smartedu.dto.StudentAlertStatusUpdateRequestDto;
import com.smartedu.dto.StudentAlertSummaryDto;
import com.smartedu.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlertController {

    private static final Set<String> SUPPORTED_STATUSES = Set.of("PENDING", "PROCESSING", "RESOLVED", "IGNORED");

    private final AlertService alertService;

    @PostMapping("/alerts/evaluate")
    public Result<List<StudentAlertRecordDto>> evaluateStudentAlerts(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long courseId) {
        if (studentId == null) {
            return Result.badRequest("Student id cannot be empty");
        }
        if (courseId == null) {
            return Result.badRequest("Course id cannot be empty");
        }
        return Result.success(alertService.evaluateStudentAlerts(studentId, courseId));
    }

    @GetMapping("/alerts/summary")
    public Result<StudentAlertSummaryDto> getAlertSummary(@RequestParam(required = false) Long courseId) {
        return Result.success(alertService.getAlertSummary(courseId));
    }

    @GetMapping("/alerts")
    public Result<List<StudentAlertRecordDto>> listAlerts(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer alertLevel,
            @RequestParam(required = false) String status) {
        if (alertLevel != null && (alertLevel < 1 || alertLevel > 3)) {
            return Result.badRequest("Invalid alert level");
        }
        String nextStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
        if (status != null && !status.isBlank() && nextStatus == null) {
            return Result.badRequest("Invalid alert status");
        }
        return Result.success(alertService.listAlerts(courseId, alertLevel, nextStatus));
    }

    @PutMapping("/alerts/{id}/status")
    public Result<StudentAlertRecordDto> updateAlertStatus(
            @PathVariable Long id,
            @RequestBody StudentAlertStatusUpdateRequestDto request) {
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            return Result.badRequest("Status cannot be empty");
        }
        String nextStatus = normalizeStatus(request.getStatus());
        if (nextStatus == null) {
            return Result.badRequest("Invalid alert status");
        }
        StudentAlertRecordDto updated = alertService.updateAlertStatus(id, nextStatus);
        if (updated == null) {
            return Result.badRequest("Invalid alert id or status");
        }
        return Result.success(updated);
    }

    @GetMapping("/student/feedback")
    public Result<List<StudentAlertRecordDto>> getStudentFeedback(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long courseId) {
        if (studentId == null) {
            return Result.badRequest("Student id cannot be empty");
        }
        return Result.success(alertService.getStudentFeedback(studentId, courseId));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String nextStatus = status.trim().toUpperCase();
        return SUPPORTED_STATUSES.contains(nextStatus) ? nextStatus : null;
    }
}
