package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传解析主链路结构化结果。
 *
 * <p>
 * 该结构作为 result-detail 接口与入库解析的统一契约。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResultDto {

    /**
     * 文档结构信息。
     */
    private DocumentStructureDto documentStructure;

    /**
     * 提取出的知识点列表。
     */
    private List<KnowledgePointDto> knowledgePoints = new ArrayList<>();

    /**
     * 思政匹配结果。
     */
    private List<IdeologyMatchDto> ideologyMatches = new ArrayList<>();

    /**
     * 生成的教学内容。
     */
    private TeachingArtifactsDto teachingArtifacts;

    /**
     * 处理告警列表。
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 是否包含推断或降级内容。
     */
    private boolean inferred;

    /**
     * 结构版本号，方便后续兼容。
     */
    private String schemaVersion;
}
