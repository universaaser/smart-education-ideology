package com.smartedu.crawler.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 爬虫原始文章数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawledArticleRaw {

    /** 站点名称 */
    private String siteName;

    /** 文章标题 */
    private String title;

    /** 原文链接 */
    private String sourceUrl;

    /** 发布时间文本（原样保留） */
    private String publishTime;

    /** 抓取到的正文 */
    private String content;
}
