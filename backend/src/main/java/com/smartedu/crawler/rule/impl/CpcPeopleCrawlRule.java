package com.smartedu.crawler.rule.impl;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 人民网-中国共产党新闻网规则
 */
@Component
@Order(1)
public class CpcPeopleCrawlRule extends AbstractCrawlSiteRule {

    public CpcPeopleCrawlRule() {
        super("人民网-中国共产党新闻网", "https://cpc.people.com.cn", "cpc.people.com.cn");
    }

    @Override
    protected boolean looksLikeArticleUrl(String url) {
        return url.contains("cpc.people.com.cn") && (url.contains(".html") || url.contains("/n1/"));
    }
}
