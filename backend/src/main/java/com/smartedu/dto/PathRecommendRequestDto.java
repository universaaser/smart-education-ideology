package com.smartedu.dto;

import lombok.Data;

import java.util.List;

/**
 * Learning-path recommendation request payload.
 */
@Data
public class PathRecommendRequestDto {

    private Long studentId;
    private List<Long> masteredNodeIds;
    private List<String> interestTags;
    private Integer maxLength;
}
