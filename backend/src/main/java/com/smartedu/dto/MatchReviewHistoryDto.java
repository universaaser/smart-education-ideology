package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchReviewHistoryDto {
    private Long id;
    private Long matchId;
    private String action;
    private String previousStatus;
    private String nextStatus;
    private String previousReason;
    private String nextReason;
    private Long reviewerId;
    private String reviewComment;
    private Integer version;
    private LocalDateTime createdAt;
}
