package com.smartedu.dto;

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
}
