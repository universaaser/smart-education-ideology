package com.smartedu.crawler.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次抓取任务的汇总结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlResult {

    /** 任务开始时间 */
    private LocalDateTime startedAt;

    /** 任务结束时间 */
    private LocalDateTime finishedAt;

    /** 每站抓取上限 */
    private Integer limitPerSite;

    /** 总抓取链接数 */
    private Integer totalFetched;

    /** 总新增资源数 */
    private Integer totalCreated;

    /** 总去重跳过数 */
    private Integer totalDeduplicated;

    /** AI 失败总数 */
    private Integer totalAiFailed;

    /** 失败总数（网络/解析/入库异常） */
    private Integer totalFailed;

    /** 总耗时（毫秒） */
    private Long durationMs;

    /** 各站点详情 */
    private List<SiteStat> siteStats = new ArrayList<>();

    /**
     * 单站点统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiteStat {
        /** 站点名称 */
        private String siteName;

        /** 列表页 URL */
        private String listUrl;

        /** 抓取到的链接数 */
        private Integer fetchedCount;

        /** 解析成功数 */
        private Integer parsedCount;

        /** 新增入库数 */
        private Integer createdCount;

        /** 去重跳过数 */
        private Integer deduplicatedCount;

        /** AI 失败数 */
        private Integer aiFailedCount;

        /** 失败数 */
        private Integer failedCount;

        /** 错误摘要（可空） */
        private String error;
    }
}
