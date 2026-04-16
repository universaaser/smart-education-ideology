package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor draft payload for teaching materials.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingMaterialDraftDto {

    private Long materialId;

    private Long parseTaskId;

    private Long userId;

    private Long courseId;

    private String title;

    private String lectureNotes;

    private List<String> cases = new ArrayList<>();

    private List<TeachingArtifactsDto.QuestionDto> questions = new ArrayList<>();

    private List<TeachingTraceItemDto> traceItems = new ArrayList<>();

    private String schemaVersion;

    private Integer versionNo;

    private String status;

    private LocalDateTime updatedAt;
}
