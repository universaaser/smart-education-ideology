package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read model for parse task history list item.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseTaskListItemDto {

    private Long taskId;

    private String fileName;

    private String status;

    private Integer progress;

    private String currentStep;

    private Long courseId;

    private String parseMode;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private String errorMessage;
}
