package com.smartedu.crawler.rule;

import com.smartedu.crawler.model.CrawledArticleRaw;
import org.jsoup.nodes.Document;

import java.util.List;

/**
 * 站点抓取规则接口
 */
public interface CrawlSiteRule {

    /** 站点名称 */
    String getSiteName();

    /** 列表页 URL */
    String getListUrl();

    /** 从列表页提取文章链接 */
    List<String> extractArticleUrls(Document listDocument, int limit);

    /** 从详情页提取文章 */
    CrawledArticleRaw parseArticle(Document articleDocument, String articleUrl);
}
