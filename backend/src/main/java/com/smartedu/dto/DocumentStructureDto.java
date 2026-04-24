package com.smartedu.dto;

import com.smartedu.dto.mineru.MineruStructuredContentDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档结构化解析结果。
 *
 * <p>
 * 该结构用于承载上传文档的章节概要与教学重点，作为后续知识点提取与生成内容的输入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStructureDto {

    /**
     * 文档标题。
     */
    private String title;

    /**
     * 文档类型，受控枚举值：TEXTBOOK / OUTLINE / PAPER / UNKNOWN。
     */
    private String documentType;

    /**
     * 文档整体概要，长度受配置限制。
     */
    private String overview;

    /**
     * 章节提纲。
     */
    private List<String> chapterOutline = new ArrayList<>();

    /**
     * 教学重点。
     */
    private List<String> teachingFocus = new ArrayList<>();

    /**
     * 原始 markdown 文本。
     *
     * <p>
     * MinerU 解析链路的主要产物，供前端直接渲染；LLM 回退链路该字段可为空。
     */
    private String rawMarkdown;

    /**
     * 解析方式标识：MINERU / FALLBACK_LLM。
     *
     * <p>
     * 用于前端标注解析来源，以及便于排查降级情况。
     */
    private String parseMode;

    /**
     * MinerU 全量结构化结果：大纲、分块、表格、图片、公式等。
     *
     * <p>
     * 回退路径（DocumentTextExtractor）不会填充该字段，前端需判空。
     */
    private MineruStructuredContentDto mineruContent;
}
