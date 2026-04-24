package com.smartedu.dto.mineru;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MinerU 结构化结果的汇总统计，供前端统计卡片与下游 LLM prompt 使用。
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MineruStatsDto {

    /** 文档总页数（取 pageIdx 最大值 + 1）。 */
    private int pageCount;

    /** 标题块数量（text_level >= 1）。 */
    private int headingCount;

    /** 正文块数量（text_level 未设或 0）。 */
    private int paragraphCount;

    /** 图片块数量。 */
    private int imageCount;

    /** 表格块数量。 */
    private int tableCount;

    /** 公式块数量。 */
    private int equationCount;

    /** 列表块数量。 */
    private int listCount;

    /** 代码/算法块数量。 */
    private int codeCount;

    /** 正文总字符数（粗略估算，供前端展示）。 */
    private int wordCount;
}
