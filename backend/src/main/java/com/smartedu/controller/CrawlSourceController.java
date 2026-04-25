package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.crawler.model.CrawlTaskStatus;
import com.smartedu.dto.CrawlSourceRequestDto;
import com.smartedu.entity.CrawlRunLog;
import com.smartedu.entity.CrawlSource;
import com.smartedu.service.CrawlSourceService;
import com.smartedu.service.ResourceCrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crawl-sources")
@RequiredArgsConstructor
public class CrawlSourceController {

    private final CrawlSourceService crawlSourceService;
    private final ResourceCrawlService resourceCrawlService;

    @GetMapping
    public Result<List<CrawlSource>> listSources() {
        return Result.success(crawlSourceService.listSources());
    }

    @PostMapping
    public Result<CrawlSource> createSource(@RequestBody CrawlSourceRequestDto request) {
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            return Result.badRequest("Source name cannot be empty");
        }
        if (request.getBaseUrl() == null || request.getBaseUrl().trim().isEmpty()) {
            return Result.badRequest("Source base URL cannot be empty");
        }
        return Result.success(crawlSourceService.createSource(request));
    }

    @PutMapping("/{id}")
    public Result<CrawlSource> updateSource(@PathVariable Long id, @RequestBody CrawlSourceRequestDto request) {
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            return Result.badRequest("Source name cannot be empty");
        }
        if (request.getBaseUrl() == null || request.getBaseUrl().trim().isEmpty()) {
            return Result.badRequest("Source base URL cannot be empty");
        }
        CrawlSource source = crawlSourceService.updateSource(id, request);
        if (source == null) {
            return Result.badRequest("Invalid crawl source");
        }
        return Result.success(source);
    }

    @PostMapping("/{id}/trigger")
    public Result<CrawlTaskStatus> triggerSource(@PathVariable Long id) {
        CrawlTaskStatus status = resourceCrawlService.startManualCrawl(id);
        return Result.success(status);
    }

    @GetMapping("/runs")
    public Result<List<CrawlRunLog>> listRunLogs(@RequestParam(required = false) Long sourceId) {
        return Result.success(crawlSourceService.listRunLogs(sourceId));
    }
}
