package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.common.PageResult;
import com.smartedu.dto.MaterialVersionItemDto;
import com.smartedu.dto.ParseTaskCorrectionDraftDto;
import com.smartedu.dto.ParseTaskListItemDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.TeachingMaterialDraftDto;
import com.smartedu.dto.TeachingMaterialSaveRequestDto;
import com.smartedu.dto.TeachingMaterialTraceDto;
import com.smartedu.dto.TeachingMaterialViewDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.service.AiIntelligenceService;
import com.smartedu.service.AiStreamBuffer;
import com.smartedu.service.ParseTaskCorrectionService;
import com.smartedu.service.TeachingMaterialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器
 * 
 * <p>
 * 处理文件上传和解析任务
 * 
 * @author SmartEducation Team
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final ParseTaskMapper parseTaskMapper;
    private final AiIntelligenceService aiIntelligenceService;
    private final TeachingMaterialService teachingMaterialService;
    private final ParseTaskCorrectionService parseTaskCorrectionService;
    private final AiStreamBuffer aiStreamBuffer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${storage.upload-path}")
    private String uploadPath;

    @Value("${storage.allowed-types}")
    private String allowedTypes;

    /**
     * 上传文件
     * 
     * @param file   上传的文件
     * @param userId 用户ID
     * @return 包含任务ID的响应
     */
    @PostMapping("/file")
    public Result<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long courseId) {

        // Upload records must stay attributable to the real operator; avoid a hidden fallback user.
        if (userId == null) {
            return Result.badRequest("User id cannot be empty");
        }

        // 1. 校验文件
        if (file.isEmpty()) {
            return Result.badRequest("请选择要上传的文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.badRequest("文件名不能为空");
        }

        // 检查文件类型
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!allowedTypes.contains(extension)) {
            return Result.badRequest("不支持的文件类型，允许：" + allowedTypes);
        }

        try {
            // 2. 创建存储目录
            // 转绝对路径，避免 MultipartFile.transferTo 把相对路径解析到 Tomcat work 目录
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 3. 生成唯一文件名
            String uniqueFilename = UUID.randomUUID().toString() + "." + extension;
            Path filePath = uploadDir.resolve(uniqueFilename);

            // 4. 保存文件
            file.transferTo(filePath.toFile());
            log.info("文件上传成功: {}", filePath);

            // 5. 创建解析任务
            ParseTask task = new ParseTask();
            task.setUserId(userId);
            task.setCourseId(courseId);
            task.setFileName(originalFilename);
            task.setFilePath(filePath.toString());
            task.setFileSize(file.getSize());
            task.setStatus("PENDING");
            task.setProgress(0);
            task.setCurrentStep("等待处理");
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            parseTaskMapper.insert(task);

            // 6. 启动异步解析任务
            task.setStatus("UPLOADING");
            task.setProgress(10);
            task.setStartedAt(LocalDateTime.now());
            parseTaskMapper.updateById(task);

            // 异步处理
            aiIntelligenceService.processDocumentAsync(task.getId());

            // 7. 返回任务信息
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", task.getId());
            result.put("fileName", originalFilename);
            result.put("fileSize", file.getSize());
            result.put("status", "UPLOADING");

            return Result.success("文件上传成功，正在解析中", result);

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取解析任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    @GetMapping("/tasks/{taskId}")
    public Result<Map<String, Object>> getTaskStatus(@PathVariable Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("任务不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("fileName", task.getFileName());
        result.put("courseId", task.getCourseId());
        result.put("status", task.getStatus());
        result.put("progress", task.getProgress());
        result.put("currentStep", task.getCurrentStep());

        // 如果已完成，返回解析结果
        if ("COMPLETED".equals(task.getStatus())) {
            result.put("parsedContent", task.getParsedContent());
            result.put("aiAnalysis", task.getAiAnalysis());
            result.put("completedAt", task.getCompletedAt());
        }

        // 如果失败，返回错误信息
        if ("FAILED".equals(task.getStatus())) {
            result.put("errorMessage", task.getErrorMessage());
        }

        return Result.success(result);
    }

    /**
     * 获取结构化流水线结果明细。
     */
    @GetMapping("/tasks/{taskId}/result-detail")
    public Result<PipelineResultDto> getTaskResultDetail(@PathVariable Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        PipelineResultDto pipelineResult = aiIntelligenceService.getPipelineResult(taskId);
        return Result.success(pipelineResult);
    }

    /**
     * 基于已有解析结果重新生成教学内容，不重复上传文件。
     */
    @PostMapping("/tasks/{taskId}/regenerate")
    public Result<PipelineResultDto> regenerateTask(@PathVariable Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        PipelineResultDto regenerated = aiIntelligenceService.regenerateTask(taskId);
        return Result.success("Pipeline regenerated", regenerated);
    }

    /**
     * 获取教师编辑草稿。
     */
    @GetMapping("/tasks/{taskId}/editor-draft")
    public Result<TeachingMaterialDraftDto> getEditorDraft(@PathVariable Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        TeachingMaterialDraftDto draft = teachingMaterialService.getEditorDraft(taskId);
        return Result.success(draft);
    }

    /**
     * 保存教师编辑草稿，不升版本。
     */
    @PutMapping("/tasks/{taskId}/editor-draft")
    public Result<TeachingMaterialDraftDto> saveEditorDraft(
            @PathVariable Long taskId,
            @RequestBody TeachingMaterialSaveRequestDto request) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        TeachingMaterialDraftDto draft = teachingMaterialService.saveDraft(taskId, request);
        return Result.success("Draft saved", draft);
    }

    /**
     * Load the current correction draft for one completed parse task.
     */
    @GetMapping("/tasks/{taskId}/correction-draft")
    public Result<ParseTaskCorrectionDraftDto> getCorrectionDraft(@PathVariable Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        try {
            ParseTaskCorrectionDraftDto draft = parseTaskCorrectionService.getCorrectionDraft(taskId);
            return Result.success(draft);
        } catch (RuntimeException ex) {
            return Result.badRequest(ex.getMessage());
        }
    }

    /**
     * Save the latest manual correction snapshot for one completed parse task.
     */
    @PutMapping("/tasks/{taskId}/correction-draft")
    public Result<ParseTaskCorrectionDraftDto> saveCorrectionDraft(
            @PathVariable Long taskId,
            @RequestBody PipelineResultDto request) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        try {
            ParseTaskCorrectionDraftDto draft = parseTaskCorrectionService.saveCorrectionDraft(taskId, request);
            return Result.success("Correction draft saved", draft);
        } catch (RuntimeException ex) {
            return Result.badRequest(ex.getMessage());
        }
    }

    /**
     * Re-run the full parsing pipeline for a completed task.
     */
    @PostMapping("/tasks/{taskId}/reparse")
    public Result<Map<String, Object>> reparseTask(@PathVariable Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        try {
            parseTaskCorrectionService.markCorrectionStale(taskId);
            ParseTask restarted = aiIntelligenceService.reparseTask(taskId);
            aiIntelligenceService.processDocumentAsync(taskId);
            return Result.success("Task reparsing started", buildTaskSummary(restarted));
        } catch (RuntimeException ex) {
            return Result.badRequest(ex.getMessage());
        }
    }

    /**
     * Retry a failed parsing task.
     */
    @PostMapping("/tasks/{taskId}/retry")
    public Result<Map<String, Object>> retryTask(@PathVariable Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        try {
            parseTaskCorrectionService.markCorrectionStale(taskId);
            ParseTask restarted = aiIntelligenceService.retryTask(taskId);
            aiIntelligenceService.processDocumentAsync(taskId);
            return Result.success("Task retry started", buildTaskSummary(restarted));
        } catch (RuntimeException ex) {
            return Result.badRequest(ex.getMessage());
        }
    }

    /**
     * 提交保存正式版本，自动升版本号。
     */
    @PostMapping("/tasks/{taskId}/materials")
    public Result<TeachingMaterialViewDto> savePublishedMaterial(
            @PathVariable Long taskId,
            @RequestBody TeachingMaterialSaveRequestDto request) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        TeachingMaterialViewDto saved = teachingMaterialService.savePublishedVersion(taskId, request);
        return Result.success("Material version saved", saved);
    }

    /**
     * Query material versions for one parse task.
     */
    @GetMapping("/tasks/{taskId}/materials")
    public Result<List<MaterialVersionItemDto>> getTaskMaterialVersions(@PathVariable Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        List<MaterialVersionItemDto> versions = teachingMaterialService.getTaskMaterialVersions(taskId);
        return Result.success(versions);
    }

    /**
     * Query searchable traces under one parse task.
     */
    @GetMapping("/tasks/{taskId}/traces")
    public Result<PageResult<TeachingMaterialTraceDto>> getTaskTraces(
            @PathVariable Long taskId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(required = false) String ideologyElement,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        PageResult<TeachingMaterialTraceDto> traces = teachingMaterialService.getTaskTracePage(
                taskId, courseId, knowledgePoint, ideologyElement, page, size);
        return Result.success(traces);
    }

    /**
     * Rollback one historical version into current draft.
     */
    @PostMapping("/tasks/{taskId}/rollback/{materialId}")
    public Result<TeachingMaterialDraftDto> rollbackMaterialVersion(
            @PathVariable Long taskId,
            @PathVariable Long materialId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        TeachingMaterialDraftDto draft = teachingMaterialService.rollbackToVersion(taskId, materialId);
        return Result.success("Rollback completed", draft);
    }

    /**
     * 增量拉取解析任务的 LLM 实时输出。
     *
     * @param taskId 任务 id
     * @param offset 上一次读到的游标；首次传 0
     * @return content=游标之后的新内容；cursor=当前总长度；status=任务当前状态
     */
    @GetMapping("/tasks/{taskId}/live-log")
    public Result<Map<String, Object>> getLiveLog(
            @PathVariable Long taskId,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            return Result.notFound("Task not found");
        }
        AiStreamBuffer.LiveLogSlice slice = aiStreamBuffer.read(taskId, offset == null ? 0 : offset);
        Map<String, Object> result = new HashMap<>();
        result.put("content", slice.content());
        result.put("cursor", slice.cursor());
        result.put("status", task.getStatus());
        result.put("currentStep", task.getCurrentStep());
        return Result.success(result);
    }

    /**
     * 历史解析任务列表，供前端"Parse History"面板使用。
     * userId 可选：传入则只返回该用户任务，否则返回全部（按创建时间倒序）。
     */
    @GetMapping("/tasks")
    public Result<PageResult<ParseTaskListItemDto>> listTasks(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<ParseTask> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(ParseTask::getUserId, userId);
        }
        wrapper.orderByDesc(ParseTask::getCreatedAt);

        Page<ParseTask> pageParam = new Page<>(safePage, safeSize);
        Page<ParseTask> pageResult = parseTaskMapper.selectPage(pageParam, wrapper);

        List<ParseTaskListItemDto> records = new java.util.ArrayList<>();
        for (ParseTask task : pageResult.getRecords()) {
            records.add(new ParseTaskListItemDto(
                    task.getId(),
                    task.getFileName(),
                    task.getStatus(),
                    task.getProgress(),
                    task.getCurrentStep(),
                    task.getCourseId(),
                    extractParseMode(task),
                    task.getCreatedAt(),
                    task.getCompletedAt(),
                    task.getErrorMessage()));
        }
        return Result.success(new PageResult<>(
                records,
                pageResult.getTotal(),
                pageResult.getSize(),
                pageResult.getCurrent()));
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }

    private Map<String, Object> buildTaskSummary(ParseTask task) {
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("fileName", task.getFileName());
        result.put("courseId", task.getCourseId());
        result.put("status", task.getStatus());
        result.put("progress", task.getProgress());
        result.put("currentStep", task.getCurrentStep());
        result.put("completedAt", task.getCompletedAt());
        result.put("errorMessage", task.getErrorMessage());
        return result;
    }

    private String extractParseMode(ParseTask task) {
        String aiAnalysis = task.getAiAnalysis();
        if (aiAnalysis == null || aiAnalysis.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(aiAnalysis);
            JsonNode parseModeNode = root.path("documentStructure").path("parseMode");
            return parseModeNode.isTextual() ? parseModeNode.asText() : null;
        } catch (Exception ex) {
            log.debug("Failed to extract parse mode for taskId={}", task.getId());
            return null;
        }
    }
}
