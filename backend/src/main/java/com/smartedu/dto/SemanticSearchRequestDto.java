package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 3A: semantic search request DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchRequestDto {

    /**
     * Search scope: knowledge_points | ideology_matches | selection_explain.
     */
    private String scope;

    /**
     * Natural language query text.
     */
    private String text;

    /**
     * Number of top results to return.
     */
    private int topK = 5;

    /**
     * Optional course filter.
     */
    private Long courseId;
}
