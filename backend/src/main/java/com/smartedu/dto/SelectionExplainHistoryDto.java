package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * History item for selected-text explanations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectionExplainHistoryDto {

    private Long recordId;

    private Long userId;

    private Long courseId;

    private Long materialId;

    private Long parseTaskId;

    private String selectedText;

    private String answer;

    private String modelReasoning;

    private Boolean hasReliableEvidence;

    private List<SelectionExplainEvidenceDto> evidenceItems;

    private LocalDateTime createdAt;
}
