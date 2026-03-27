package com.smartedu.crawler.model;

/**
 * 抓取任务状态
 */
public enum CrawlTaskState {
    IDLE,
    RUNNING,
    STOP_REQUESTED,
    STOPPED,
    COMPLETED,
    FAILED
}
