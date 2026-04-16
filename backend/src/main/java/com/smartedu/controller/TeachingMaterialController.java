package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.common.PageResult;
import com.smartedu.dto.TeachingMaterialTraceDto;
import com.smartedu.dto.TeachingMaterialViewDto;
import com.smartedu.service.TeachingMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Teaching material query controller.
 */
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class TeachingMaterialController {

    private final TeachingMaterialService teachingMaterialService;

    /**
     * Read one teaching material version by id.
     */
    @GetMapping("/{materialId}")
    public Result<TeachingMaterialViewDto> getMaterialById(@PathVariable Long materialId) {
        TeachingMaterialViewDto material = teachingMaterialService.getMaterialById(materialId);
        return Result.success(material);
    }

    /**
     * Query searchable traces under one material version.
     */
    @GetMapping("/{materialId}/traces")
    public Result<PageResult<TeachingMaterialTraceDto>> getMaterialTraces(
            @PathVariable Long materialId,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(required = false) String ideologyElement,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<TeachingMaterialTraceDto> traces = teachingMaterialService.getMaterialTracePage(
                materialId, knowledgePoint, ideologyElement, page, size);
        return Result.success(traces);
    }

    /**
     * Export one material version as markdown file.
     */
    @GetMapping("/{materialId}/export/markdown")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable Long materialId) {
        String markdown = teachingMaterialService.exportMarkdownByMaterialId(materialId);
        String fileName = teachingMaterialService.buildMarkdownFileName(materialId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        return ResponseEntity.ok()
                .headers(headers)
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }
}
