package com.smartedu.dto;

import lombok.Data;

@Data
public class MatchReviewUpdateRequestDto {
    private Long reviewerId;
    private String matchReason;
    private String reviewComment;
}
