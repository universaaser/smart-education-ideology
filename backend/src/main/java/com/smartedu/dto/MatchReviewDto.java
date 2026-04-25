package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchReviewDto {
    private Long id;
    private Long subjectKnowledgeId;
    private String subjectKnowledgeName;
    private String subject;
    private String category;
    private Long ideologyKnowledgeId;
    private String ideologyName;
    private String ideologyDescription;
    private Integer isPrimary;
    private BigDecimal matchScore;
    private String matchReason;
    private String reviewStatus;
    private Integer version;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private LocalDateTime createdAt;
}
