package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.entity.ParseTask;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.service.AiIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
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
            @RequestParam(defaultValue = "1") Long userId) {

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
            Path uploadDir = Paths.get(uploadPath);
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
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }
}
