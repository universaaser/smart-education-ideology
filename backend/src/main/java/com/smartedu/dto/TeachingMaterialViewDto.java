package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Teaching material read model.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingMaterialViewDto {

    private Long materialId;

    private Long parseTaskId;

    private Long userId;

    private Long courseId;

    private Long chapterId;

    private String title;

    private String lectureNotes;

    private List<String> cases = new ArrayList<>();

    private List<TeachingArtifactsDto.QuestionDto> questions = new ArrayList<>();

    private List<TeachingTraceItemDto> traceItems = new ArrayList<>();

    private String schemaVersion;

    private Integer versionNo;

    private String status;

    private Integer isLatest;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
