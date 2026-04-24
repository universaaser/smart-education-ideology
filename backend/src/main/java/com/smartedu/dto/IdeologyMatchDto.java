package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 思政元素匹配结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdeologyMatchDto {

    /**
     * 对应知识点名称。
     */
    private String knowledgePointName;

    /**
     * 匹配到的思政元素名称。
     */
    private String ideologyElement;

    /**
     * 匹配理由。
     */
    private String matchReason;

    /**
     * 匹配置信度，0-100。
     */
    private Integer confidence;

    /**
     * 基于资源引用的解释。
     */
    private String citationExplanation;

    /**
     * 支撑该匹配的资源引用。
     */
    private List<ResourceCitationDto> resourceCitations = new ArrayList<>();
}
