package com.smartedu.crawler.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 处理后的文章数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawledArticleProcessed {

    /** 标题 */
    private String title;

    /** 来源站点 */
    private String source;

    /** 原文链接 */
    private String sourceUrl;

    /** 入库存储短摘录 */
    private String excerpt;

    /** 思政价值摘要 */
    private String ideologySummary;

    /** 标签 JSON 字符串 */
    private String tagsJson;

    /** 是否由 AI 成功生成 */
    private boolean aiSuccess;
}
