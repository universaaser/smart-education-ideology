package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Trace row for searchable trace APIs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingMaterialTraceDto {

    private Long id;

    private Long materialId;

    private Long parseTaskId;

    private Long courseId;

    private Long knowledgePointId;

    private String knowledgePointName;

    private String ideologyElement;

    private String evidenceSnippet;

    private String matchReason;

    private String resourceTitle;

    private String resourceSource;

    private String resourceSourceUrl;

    private String resourceQuotedExcerpt;

    private String citationExplanation;

    private LocalDateTime createdAt;
}
