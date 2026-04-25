package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordTaskItemDto {

    private Long id;

    private Long taskId;

    private String keyword;

    private String title;

    private String sourceUrl;

    private String excerpt;

    private String aiSummary;

    private String ideologyTags;

    private String status;

    private Long resourceId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
