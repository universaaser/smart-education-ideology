package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.StudentQuizQuestionDto;
import com.smartedu.dto.StudentQuizSubmitRequestDto;
import com.smartedu.dto.StudentQuizSubmitResultDto;
import com.smartedu.service.StudentQuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/quiz")
@RequiredArgsConstructor
public class StudentQuizController {

    private final StudentQuizService studentQuizService;

    @GetMapping("/questions")
    public Result<List<StudentQuizQuestionDto>> listQuestions(@RequestParam(required = false) Long materialId) {
        if (materialId == null) {
            return Result.badRequest("Material id cannot be empty");
        }
        return Result.success(studentQuizService.listQuestions(materialId));
    }

    @PostMapping("/submit")
    public Result<StudentQuizSubmitResultDto> submitAnswer(@RequestBody StudentQuizSubmitRequestDto request) {
        if (request == null || request.getStudentId() == null) {
            return Result.badRequest("Student id cannot be empty");
        }
        if (request.getMaterialId() == null) {
            return Result.badRequest("Material id cannot be empty");
        }
        if (request.getCourseId() == null) {
            return Result.badRequest("Course id cannot be empty");
        }
        if (request.getQuestionIndex() == null) {
            return Result.badRequest("Question index cannot be empty");
        }
        if (request.getAnswer() == null || request.getAnswer().isBlank()) {
            return Result.badRequest("Answer cannot be empty");
        }
        try {
            return Result.success(studentQuizService.submitAnswer(request));
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }
}
