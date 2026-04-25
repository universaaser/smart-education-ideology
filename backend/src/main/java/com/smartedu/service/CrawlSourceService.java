package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.crawler.model.CrawlResult;
import com.smartedu.crawler.rule.CrawlSiteRule;
import com.smartedu.dto.CrawlSourceRequestDto;
import com.smartedu.entity.CrawlRunLog;
import com.smartedu.entity.CrawlSource;
import com.smartedu.mapper.CrawlRunLogMapper;
import com.smartedu.mapper.CrawlSourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CrawlSourceService {

    private final CrawlSourceMapper crawlSourceMapper;
    private final CrawlRunLogMapper crawlRunLogMapper;
    private final ObjectMapper objectMapper;

    public List<CrawlSource> listSources() {
        return crawlSourceMapper.selectList(new LambdaQueryWrapper<CrawlSource>()
                .orderByDesc(CrawlSource::getEnabled)
                .orderByAsc(CrawlSource::getId));
    }

    public List<CrawlRunLog> listRunLogs(Long sourceId) {
        LambdaQueryWrapper<CrawlRunLog> wrapper = new LambdaQueryWrapper<>();
        if (sourceId != null) {
            wrapper.eq(CrawlRunLog::getSourceId, sourceId);
        }
        wrapper.orderByDesc(CrawlRunLog::getStartedAt)
                .orderByDesc(CrawlRunLog::getId)
                .last("LIMIT 50");
        return crawlRunLogMapper.selectList(wrapper);
    }

    @Transactional
    public CrawlSource createSource(CrawlSourceRequestDto request) {
        CrawlSource source = new CrawlSource();
        applyRequest(source, request);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        crawlSourceMapper.insert(source);
        return source;
    }

    @Transactional
    public CrawlSource updateSource(Long id, CrawlSourceRequestDto request) {
        CrawlSource source = crawlSourceMapper.selectById(id);
        if (source == null) {
            return null;
        }
        applyRequest(source, request);
        source.setUpdatedAt(LocalDateTime.now());
        crawlSourceMapper.updateById(source);
        return source;
    }

    public List<CrawlSiteRule> filterEnabledRules(List<CrawlSiteRule> rules) {
        return filterEnabledRules(rules, null);
    }

    public List<CrawlSiteRule> filterEnabledRules(List<CrawlSiteRule> rules, Long sourceId) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<CrawlSource> sources = listSources();
        if (sources.isEmpty()) {
            seedSourcesFromRules(rules);
            sources = listSources();
        }
        if (sourceId != null) {
            CrawlSource source = crawlSourceMapper.selectById(sourceId);
            sources = source == null ? List.of() : List.of(source);
        }

        Set<String> enabledKeys = sources.stream()
                .filter(source -> source.getEnabled() != null && source.getEnabled() == 1)
                .map(this::sourceKey)
                .collect(Collectors.toSet());
        if (enabledKeys.isEmpty()) {
            return List.of();
        }

        List<CrawlSiteRule> filtered = new ArrayList<>();
        for (CrawlSiteRule rule : rules) {
            if (enabledKeys.contains(ruleKey(rule))) {
                filtered.add(rule);
            }
        }
        return filtered;
    }

    @Transactional
    public void recordEmptyRun(Long sourceId, LocalDateTime startedAt, LocalDateTime finishedAt, String status, String errorSummary) {
        CrawlSource source = sourceId == null ? null : crawlSourceMapper.selectById(sourceId);
        CrawlRunLog log = new CrawlRunLog();
        log.setSourceId(sourceId);
        log.setSourceName(source == null ? "All sources" : source.getName());
        log.setStatus(status == null || status.isBlank() ? "FAILED" : status);
        log.setTotalFetched(0);
        log.setTotalCreated(0);
        log.setTotalDeduplicated(0);
        log.setTotalFailed(0);
        log.setErrorSummary(trimToLength(errorSummary == null ? "" : errorSummary, 500));
        log.setStatsJson("{}");
        log.setStartedAt(startedAt);
        log.setFinishedAt(finishedAt);
        log.setCreatedAt(LocalDateTime.now());
        crawlRunLogMapper.insert(log);

        if (source != null) {
            source.setLastRunAt(finishedAt == null ? LocalDateTime.now() : finishedAt);
            source.setUpdatedAt(LocalDateTime.now());
            crawlSourceMapper.updateById(source);
        }
    }

    @Transactional
    public void recordRun(CrawlResult result, String status, String errorSummary) {
        if (result == null || result.getSiteStats() == null) {
            return;
        }

        Map<String, CrawlSource> sourceByKey = listSources().stream()
                .collect(Collectors.toMap(this::sourceKey, source -> source, (first, second) -> first));

        for (CrawlResult.SiteStat stat : result.getSiteStats()) {
            CrawlSource source = sourceByKey.get(siteStatKey(stat));
            CrawlRunLog log = new CrawlRunLog();
            log.setSourceId(source == null ? null : source.getId());
            log.setSourceName(stat.getSiteName());
            log.setStatus(resolveSiteStatus(status, stat));
            log.setTotalFetched(valueOrZero(stat.getFetchedCount()));
            log.setTotalCreated(valueOrZero(stat.getCreatedCount()));
            log.setTotalDeduplicated(valueOrZero(stat.getDeduplicatedCount()));
            log.setTotalFailed(valueOrZero(stat.getFailedCount()));
            log.setErrorSummary(firstNonBlank(stat.getError(), errorSummary));
            log.setStatsJson(writeStatsJson(stat));
            log.setStartedAt(result.getStartedAt());
            log.setFinishedAt(result.getFinishedAt());
            log.setCreatedAt(LocalDateTime.now());
            crawlRunLogMapper.insert(log);

            if (source != null) {
                source.setLastRunAt(result.getFinishedAt() == null ? LocalDateTime.now() : result.getFinishedAt());
                source.setUpdatedAt(LocalDateTime.now());
                crawlSourceMapper.updateById(source);
            }
        }
    }

    private void seedSourcesFromRules(List<CrawlSiteRule> rules) {
        for (CrawlSiteRule rule : rules) {
            CrawlSource source = new CrawlSource();
            source.setName(rule.getSiteName());
            source.setBaseUrl(rule.getListUrl());
            source.setEnabled(1);
            source.setRemark("Seeded from crawler rule");
            source.setCreatedAt(LocalDateTime.now());
            source.setUpdatedAt(LocalDateTime.now());
            crawlSourceMapper.insert(source);
        }
    }

    private void applyRequest(CrawlSource source, CrawlSourceRequestDto request) {
        source.setName(trimToLength(request == null ? "" : request.getName(), 120));
        source.setBaseUrl(trimToLength(request == null ? "" : request.getBaseUrl(), 500));
        source.setEnabled(request != null && Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        source.setRemark(trimToLength(request == null ? "" : request.getRemark(), 500));
    }

    private String resolveSiteStatus(String status, CrawlResult.SiteStat stat) {
        if (stat != null && stat.getError() != null && !stat.getError().isBlank()) {
            return "FAILED";
        }
        return status == null || status.isBlank() ? "DONE" : status;
    }

    private String writeStatsJson(CrawlResult.SiteStat stat) {
        try {
            return objectMapper.writeValueAsString(stat);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String sourceKey(CrawlSource source) {
        return normalizeKey(source == null ? null : source.getBaseUrl());
    }

    private String ruleKey(CrawlSiteRule rule) {
        return normalizeKey(rule == null ? null : rule.getListUrl());
    }

    private String siteStatKey(CrawlResult.SiteStat stat) {
        return normalizeKey(stat == null ? null : stat.getListUrl());
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return trimToLength(first, 500);
        }
        return trimToLength(second == null ? "" : second, 500);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToLength(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
