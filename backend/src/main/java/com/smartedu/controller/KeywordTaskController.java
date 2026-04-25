package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.KeywordTaskCreateRequestDto;
import com.smartedu.dto.KeywordTaskDto;
import com.smartedu.entity.Resource;
import com.smartedu.service.KeywordTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/keyword-tasks")
@RequiredArgsConstructor
public class KeywordTaskController {

    private final KeywordTaskService keywordTaskService;

    @GetMapping
    public Result<List<KeywordTaskDto>> listTasks(@RequestParam(required = false) Long courseId) {
        return Result.success(keywordTaskService.listTasks(courseId));
    }

    @GetMapping("/{id}")
    public Result<KeywordTaskDto> getTask(@PathVariable Long id) {
        KeywordTaskDto task = keywordTaskService.getTask(id);
        if (task == null) {
            return Result.notFound("Keyword task not found");
        }
        return Result.success(task);
    }

    @PostMapping
    public Result<KeywordTaskDto> createTask(@RequestBody KeywordTaskCreateRequestDto request) {
        if (request == null || request.getCourseId() == null) {
            return Result.badRequest("Course id cannot be empty");
        }
        if (keywordTaskService.normalizeKeywords(request.getKeywords()).isEmpty()) {
            return Result.badRequest("Keywords cannot be empty");
        }
        return Result.success(keywordTaskService.createTask(request));
    }

    @PostMapping("/{id}/run")
    public Result<KeywordTaskDto> runTask(@PathVariable Long id) {
        KeywordTaskDto task = keywordTaskService.runTask(id);
        if (task == null) {
            return Result.notFound("Keyword task not found");
        }
        return Result.success(task);
    }

    @PostMapping("/{id}/items/{itemId}/accept")
    public Result<Resource> acceptItem(@PathVariable Long id, @PathVariable Long itemId) {
        Resource resource = keywordTaskService.acceptItem(id, itemId);
        if (resource == null) {
            return Result.badRequest("Invalid keyword task item");
        }
        return Result.success(resource);
    }
}
