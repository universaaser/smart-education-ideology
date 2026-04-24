package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源引用对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceCitationDto {

    private String resourceRefId;

    private Long resourceId;

    private String title;

    private String source;

    private String sourceUrl;

    private String quotedExcerpt;

    private String citationReason;
}
