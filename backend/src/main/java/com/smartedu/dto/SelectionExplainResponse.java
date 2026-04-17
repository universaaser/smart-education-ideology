package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文本片段解释响应
 *
 * <p>
 * 当前先提供后端能力底座，前端后续接入时可以直接复用该结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectionExplainResponse {

    private Long recordId;

    private String answer;

    private String modelReasoning;

    private Boolean hasReliableEvidence;

    private List<SelectionExplainEvidenceDto> evidenceItems;

    private List<KnowledgeContextItem> contexts;

    private LocalDateTime createdAt;

    public SelectionExplainResponse(String answer, List<KnowledgeContextItem> contexts) {
        this.answer = answer;
        this.modelReasoning = answer;
        this.hasReliableEvidence = contexts != null && !contexts.isEmpty();
        this.evidenceItems = List.of();
        this.contexts = contexts;
    }
}
