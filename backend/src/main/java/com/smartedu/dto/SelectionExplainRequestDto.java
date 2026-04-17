package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for selected-text explanation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectionExplainRequestDto {

    private String text;

    private Long userId;

    private Long courseId;

    private Long materialId;

    private Long parseTaskId;
}
