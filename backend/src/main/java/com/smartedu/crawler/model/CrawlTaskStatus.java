package com.smartedu.crawler.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 抓取任务运行状态快照
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlTaskStatus {

    /** 任务状态: IDLE/RUNNING/STOP_REQUESTED/STOPPED/COMPLETED/FAILED */
    private String state;

    /** 是否已收到停止请求 */
    private Boolean stopRequested;

    /** 任务开始时间 */
    private LocalDateTime startedAt;

    /** 任务结束时间 */
    private LocalDateTime finishedAt;

    /** 当前正在处理的站点 */
    private String currentSite;

    /** 当前正在处理的文章链接 */
    private String currentUrl;

    /** 每站新增目标条数 */
    private Integer targetCreatedPerSite;

    /** 每站候选链接抓取上限 */
    private Integer candidateLimitPerSite;

    /** 当前任务累计结果（运行中为实时快照） */
    private CrawlResult currentResult;

    /** 最近一次结束任务结果 */
    private CrawlResult lastResult;

    /** 状态描述信息 */
    private String message;
}
