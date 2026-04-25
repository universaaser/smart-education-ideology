package com.smartedu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.crawler.model.CrawledArticleProcessed;
import com.smartedu.crawler.model.CrawledArticleRaw;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCrawlServiceTest {

    private static final String TAG_CRAFTSMANSHIP = "\u5de5\u5320\u7cbe\u795e";

    @Test
    void shouldKeepEmptyTagsWhenFallbackProcessIsUsed() throws Exception {
        ResourceCrawlService service = new ResourceCrawlService(List.of(), null, null, null, new ObjectMapper(), null);
        CrawledArticleRaw raw = new CrawledArticleRaw(
                "People Daily",
                "Industrial Sensor Network",
                "https://example.com/article",
                "2026-04-22",
                "Industrial sensor networks improve production safety, enable real-time monitoring, "
                        + "and support smart manufacturing upgrades through accurate sensing and stable data collection.");

        CrawledArticleProcessed processed = invokeFallbackProcess(service, raw);

        assertEquals("[]", processed.getTagsJson());
        assertEquals("", processed.getIdeologySummary());
        assertTrue(processed.getExcerpt().contains("Industrial sensor networks improve production safety"));
        assertFalse(processed.isAiSuccess());
    }

    @Test
    void shouldReturnEmptyArrayWhenAiTagsAreUnknown() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ResourceCrawlService service = new ResourceCrawlService(List.of(), null, null, null, objectMapper, null);
        JsonNode tagNode = objectMapper.readTree("[\"Unknown Tag\"]");

        String tagsJson = invokeBuildTagsJson(service, tagNode);

        assertEquals("[]", tagsJson);
    }

    @Test
    void shouldCanonicalizeSupportedIdeologyTags() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ResourceCrawlService service = new ResourceCrawlService(List.of(), null, null, null, objectMapper, null);
        JsonNode tagNode = objectMapper.readTree("[\"" + TAG_CRAFTSMANSHIP + "\"]");

        String tagsJson = invokeBuildTagsJson(service, tagNode);

        assertEquals("[\"" + TAG_CRAFTSMANSHIP + "\"]", tagsJson);
    }

    @Test
    void shouldReturnEmptyArrayWhenAiTagsAreEmptyArray() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ResourceCrawlService service = new ResourceCrawlService(List.of(), null, null, null, objectMapper, null);
        JsonNode tagNode = objectMapper.readTree("[]");

        String tagsJson = invokeBuildTagsJson(service, tagNode);

        assertEquals("[]", tagsJson);
    }

    @Test
    void shouldRecordFailedRunWhenSelectedSourceHasNoMatchingRule() throws Exception {
        StubCrawlSourceService crawlSourceService = new StubCrawlSourceService();
        ResourceCrawlService service = new ResourceCrawlService(List.of(), null, null, null, new ObjectMapper(), crawlSourceService);

        invokeRunManualCrawl(service, 7L);

        assertTrue(crawlSourceService.emptyRunRecorded.get());
    }

    private CrawledArticleProcessed invokeFallbackProcess(ResourceCrawlService service, CrawledArticleRaw raw) throws Exception {
        Method method = ResourceCrawlService.class.getDeclaredMethod("fallbackProcess", CrawledArticleRaw.class);
        method.setAccessible(true);
        return (CrawledArticleProcessed) method.invoke(service, raw);
    }

    private String invokeBuildTagsJson(ResourceCrawlService service, JsonNode tagNode) throws Exception {
        Method method = ResourceCrawlService.class.getDeclaredMethod("buildTagsJson", JsonNode.class);
        method.setAccessible(true);
        return (String) method.invoke(service, tagNode);
    }

    private void invokeRunManualCrawl(ResourceCrawlService service, Long sourceId) throws Exception {
        Method method = ResourceCrawlService.class.getDeclaredMethod("runManualCrawl", Long.class);
        method.setAccessible(true);
        method.invoke(service, sourceId);
    }

    private static class StubCrawlSourceService extends CrawlSourceService {

        private final AtomicBoolean emptyRunRecorded = new AtomicBoolean(false);

        StubCrawlSourceService() {
            super(null, null, null);
        }

        @Override
        public List<com.smartedu.crawler.rule.CrawlSiteRule> filterEnabledRules(
                List<com.smartedu.crawler.rule.CrawlSiteRule> rules,
                Long sourceId) {
            return List.of();
        }

        @Override
        public void recordEmptyRun(Long sourceId, LocalDateTime startedAt, LocalDateTime finishedAt, String status, String errorSummary) {
            if (sourceId == 7L
                    && "FAILED".equals(status)
                    && errorSummary.contains("No enabled crawler rule matched")) {
                emptyRunRecorded.set(true);
            }
        }
    }
}
