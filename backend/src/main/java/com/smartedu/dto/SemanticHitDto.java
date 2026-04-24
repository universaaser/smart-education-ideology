package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 3A: single semantic search hit DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticHitDto {

    private String id;
    private double score;
    private String title;
    private String snippet;
    private String sourceType;
    private Long sourceId;
    private String source;
    private String sourceUrl;
}
