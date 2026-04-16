package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Teaching trace item.
 *
 * <p>
 * Each item links generated teaching output back to pipeline evidence.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingTraceItemDto {

    private Long parseTaskId;

    private String knowledgePointName;

    private Long knowledgePointId;

    private String ideologyElement;

    private String evidenceSnippet;

    private String matchReason;
}
