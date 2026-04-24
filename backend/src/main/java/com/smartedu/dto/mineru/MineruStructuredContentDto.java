package com.smartedu.dto.mineru;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * MinerU 解析后的全量结构化结果。
 *
 * <p>
 * 由 {@code content_list.json} 转译而来；图片/表格/公式的静态资源已替换成后端可访问的 URL。
 * 该对象会序列化进 ParseTask.aiAnalysis（PipelineResultDto 的一部分），作为前端富展示与下游
 * LLM prompt 的共享事实源。
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MineruStructuredContentDto {

    /** MinerU 返回的 batchId，用于追溯与资源 URL 的一部分。 */
    private String batchId;

    /** 使用的 model_version（vlm/MinerU-HTML 等）。 */
    private String modelVersion;

    /** 资源目录 URL 前缀，例如 {@code /api/upload/mineru-assets/<batchId>/}。 */
    private String assetBaseUrl;

    /** 扁平的 content block 列表（与 MinerU content_list 顺序一致）。 */
    private List<MineruContentBlockDto> blocks = new ArrayList<>();

    /** 基于 heading 折叠出的大纲树。 */
    private List<MineruOutlineNodeDto> outline = new ArrayList<>();

    /** 汇总统计。 */
    private MineruStatsDto stats;

    /** 纯图片块的便捷索引，便于前端“图片”Tab。 */
    private List<MineruContentBlockDto> images = new ArrayList<>();

    /** 纯表格块的便捷索引。 */
    private List<MineruContentBlockDto> tables = new ArrayList<>();

    /** 纯公式块的便捷索引。 */
    private List<MineruContentBlockDto> equations = new ArrayList<>();
}
