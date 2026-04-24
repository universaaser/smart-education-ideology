package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read model for parse task correction draft.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseTaskCorrectionDraftDto {

    private String source;

    private boolean stale;

    private LocalDateTime savedAt;

    private LocalDateTime sourceCompletedAt;

    private PipelineResultDto result;
}
