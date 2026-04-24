package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识点结构化对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePointDto {

    /**
     * 知识点名称。
     */
    private String pointName;

    /**
     * 知识点定义或解释。
     */
    private String definition;

    /**
     * 所属章节。
     */
    private String chapter;

    /**
     * 重要度，受控枚举值：HIGH / MEDIUM / LOW。
     */
    private String importance;

    /**
     * 证据片段，用于后续可追溯展示。
     */
    private String evidenceSnippet;

    /**
     * 来自爬虫资源的补充引用。
     */
    private List<ResourceCitationDto> resourceCitations = new ArrayList<>();
}
