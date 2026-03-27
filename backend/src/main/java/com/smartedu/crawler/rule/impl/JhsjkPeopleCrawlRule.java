package com.smartedu.crawler.rule.impl;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 习近平系列重要讲话数据库规则
 */
@Component
@Order(4)
public class JhsjkPeopleCrawlRule extends AbstractCrawlSiteRule {

    public JhsjkPeopleCrawlRule() {
        super("习近平系列重要讲话数据库", "http://jhsjk.people.cn", "jhsjk.people.cn");
    }

    @Override
    protected boolean looksLikeArticleUrl(String url) {
        return url.contains("jhsjk.people.cn") &&
                (url.contains(".html") || url.contains("/article") || url.contains("/detail"));
    }
}
