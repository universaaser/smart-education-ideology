package com.smartedu.crawler.rule.impl;

import com.smartedu.crawler.model.CrawledArticleRaw;
import com.smartedu.crawler.rule.CrawlSiteRule;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用站点规则基类
 */
public abstract class AbstractCrawlSiteRule implements CrawlSiteRule {

    private final String siteName;
    private final String listUrl;
    private final String hostKeyword;

    protected AbstractCrawlSiteRule(String siteName, String listUrl, String hostKeyword) {
        this.siteName = siteName;
        this.listUrl = listUrl;
        this.hostKeyword = hostKeyword;
    }

    @Override
    public String getSiteName() {
        return siteName;
    }

    @Override
    public String getListUrl() {
        return listUrl;
    }

    @Override
    public List<String> extractArticleUrls(Document listDocument, int limit) {
        Set<String> urls = new LinkedHashSet<>();
        for (Element link : listDocument.select("a[href]")) {
            String absUrl = normalizeUrl(link.attr("abs:href"));
            if (absUrl.isEmpty()) {
                continue;
            }
            if (!absUrl.contains(hostKeyword)) {
                continue;
            }
            if (!looksLikeArticleUrl(absUrl)) {
                continue;
            }
            urls.add(absUrl);
            if (urls.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(urls);
    }

    @Override
    public CrawledArticleRaw parseArticle(Document articleDocument, String articleUrl) {
        String title = firstNonBlank(articleDocument,
                "meta[property=og:title]",
                "meta[name=Title]",
                "h1",
                ".title",
                ".article-title",
                "#p-detail h1");

        String content = extractMainContent(articleDocument);
        if (title.isBlank() || content.isBlank()) {
            return null;
        }

        String publishTime = firstNonBlank(articleDocument,
                "meta[property=article:published_time]",
                "time",
                ".time",
                ".publish-time",
                ".date");

        return new CrawledArticleRaw(
                siteName,
                title,
                articleUrl,
                publishTime,
                content);
    }

    /**
     * 站点可重写 URL 判定
     */
    protected boolean looksLikeArticleUrl(String url) {
        return url.contains(".html") || url.contains("/20") || url.contains("/n") || url.contains("/p/");
    }

    /**
     * 站点可重写正文提取
     */
    protected String extractMainContent(Document doc) {
        String[] selectors = {
                "article",
                ".article",
                ".article-content",
                ".content",
                ".main-content",
                "#p-detail",
                ".text",
                ".detail"
        };

        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element != null) {
                String text = normalizeText(element.text());
                if (text.length() >= 120) {
                    return text;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Element p : doc.select("p")) {
            String t = normalizeText(p.text());
            if (t.length() >= 8) {
                sb.append(t).append("\n");
            }
        }
        return normalizeText(sb.toString());
    }

    protected String firstNonBlank(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String value;
            if (element.hasAttr("content")) {
                value = element.attr("content");
            } else if (element.hasAttr("datetime")) {
                value = element.attr("datetime");
            } else {
                value = element.text();
            }

            value = normalizeText(value);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    protected String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("javascript:")) {
            return "";
        }
        if (trimmed.startsWith("mailto:")) {
            return "";
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "";
        }
        return trimmed;
    }
}
