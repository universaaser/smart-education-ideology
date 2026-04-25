package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordTaskDto {

    private Long id;

    private Long courseId;

    private Long creatorId;

    private List<String> keywords;

    private String status;

    private String resultSummary;

    private String errorSummary;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<KeywordTaskItemDto> items;
}
