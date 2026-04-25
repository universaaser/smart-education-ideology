package com.smartedu.dto;

import lombok.Data;

@Data
public class ResourceReviewStatusUpdateRequestDto {

    private String reviewStatus;

    private Long reviewerId;
}
