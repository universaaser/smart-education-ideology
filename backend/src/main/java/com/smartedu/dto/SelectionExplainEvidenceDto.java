package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evidence item shown separately from model reasoning.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectionExplainEvidenceDto {

    private String evidenceType;

    private Long referenceId;

    private String title;

    private String summary;

    private String source;

    private String sourceUrl;
}
