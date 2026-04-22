package com.smartedu.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.crawler.model.CrawlResult;
import com.smartedu.crawler.model.CrawlTaskState;
import com.smartedu.crawler.model.CrawlTaskStatus;
import com.smartedu.crawler.model.CrawledArticleProcessed;
import com.smartedu.crawler.model.CrawledArticleRaw;
import com.smartedu.crawler.rule.CrawlSiteRule;
import com.smartedu.entity.Resource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crawl + clean + AI summarize + save resources.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceCrawlService {

    private static final int TARGET_CREATED_PER_SITE = 2;
    private static final int CANDIDATE_LIMIT_PER_SITE = 60;
    private static final int EXCERPT_MAX_LENGTH = 320;
    private static final int CONTENT_FOR_AI_MAX_LENGTH = 2200;
    private static final int MIN_CONTENT_LENGTH = 80;
    private static final String DEFAULT_CATEGORY = "\u65f6\u653f\u8d44\u8baf";
    private static final String EMPTY_TAGS_JSON = "[]";
    private static final List<String> ALLOWED_IDEOLOGY_TAGS = List.of(
            "\u5de5\u5320\u7cbe\u795e",
            "\u4ea7\u4e1a\u521b\u65b0",
            "\u79d1\u6280\u62a5\u56fd",
            "\u5bb6\u56fd\u60c5\u6000",
            "\u8d23\u4efb\u62c5\u5f53",
            "\u7eff\u8272\u53d1\u5c55",
            "\u4f9d\u6cd5\u6cbb\u7406",
            "\u804c\u4e1a\u64cd\u5b88",
            "\u534f\u540c\u5171\u6cbb",
            "\u6587\u5316\u81ea\u4fe1");

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private static final Set<String> TRACKING_QUERY_KEYS = Set.of(
            "spm", "from", "ref", "refer", "share", "sharefrom");

    private final List<CrawlSiteRule> crawlSiteRules;
    private final ResourceService resourceService;
    private final AiIntelligenceService aiIntelligenceService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final ObjectMapper objectMapper;

    private final Object taskLock = new Object();
    private final ExecutorService crawlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "resource-crawl-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile CrawlTaskState taskState = CrawlTaskState.IDLE;
    private volatile boolean stopRequested = false;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile String currentSite = "";
    private volatile String currentUrl = "";
    private volatile CrawlResult currentResult;
    private volatile CrawlResult lastResult;
    private volatile String statusMessage = "idle";

    public CrawlTaskStatus startManualCrawl() {
        synchronized (taskLock) {
            if (isTaskActive(taskState)) {
                statusMessage = "task already running";
                return snapshotStatus();
            }

            stopRequested = false;
            taskState = CrawlTaskState.RUNNING;
            startedAt = LocalDateTime.now();
            finishedAt = null;
            currentSite = "";
            currentUrl = "";
            currentResult = initCrawlResult(startedAt);
            statusMessage = "task started";

            crawlExecutor.submit(this::runManualCrawl);
            return snapshotStatus();
        }
    }

    public CrawlTaskStatus requestStop() {
        synchronized (taskLock) {
            if (!isTaskActive(taskState)) {
                statusMessage = "no running task";
                return snapshotStatus();
            }

            stopRequested = true;
            if (taskState == CrawlTaskState.RUNNING) {
                taskState = CrawlTaskState.STOP_REQUESTED;
            }
            statusMessage = "stop requested";
            return snapshotStatus();
        }
    }

    public CrawlTaskStatus getTaskStatus() {
        return snapshotStatus();
    }

    private void runManualCrawl() {
        long startedMs = System.currentTimeMillis();
        CrawlResult result = initCrawlResult(startedAt != null ? startedAt : LocalDateTime.now());

        try {
            List<CrawlResult.SiteStat> siteStats = new ArrayList<>();
            Set<String> seenArticleFingerprints = new LinkedHashSet<>();

            for (CrawlSiteRule rule : crawlSiteRules) {
                if (shouldStopBeforeNextItem()) {
                    break;
                }

                setCurrentSite(rule.getSiteName());
                CrawlResult.SiteStat stat = crawlSingleSite(
                        rule,
                        TARGET_CREATED_PER_SITE,
                        CANDIDATE_LIMIT_PER_SITE,
                        seenArticleFingerprints);
                siteStats.add(stat);
                mergeResult(result, stat);
            }

            result.setSiteStats(siteStats);
            result.setFinishedAt(LocalDateTime.now());
            result.setDurationMs(System.currentTimeMillis() - startedMs);

            synchronized (taskLock) {
                finishedAt = result.getFinishedAt();
                currentSite = "";
                currentUrl = "";
                currentResult = copyCrawlResult(result);
                lastResult = copyCrawlResult(result);

                if (stopRequested) {
                    taskState = CrawlTaskState.STOPPED;
                    statusMessage = "task stopped";
                } else {
                    taskState = CrawlTaskState.COMPLETED;
                    statusMessage = "task completed";
                }
            }
        } catch (Exception e) {
            log.error("crawl task failed", e);

            result.setFinishedAt(LocalDateTime.now());
            result.setDurationMs(System.currentTimeMillis() - startedMs);

            synchronized (taskLock) {
                finishedAt = result.getFinishedAt();
                currentSite = "";
                currentUrl = "";
                currentResult = copyCrawlResult(result);
                lastResult = copyCrawlResult(result);
                taskState = CrawlTaskState.FAILED;
                statusMessage = "task failed: " + e.getMessage();
            }
        } finally {
            synchronized (taskLock) {
                stopRequested = false;
            }
        }
    }

    private CrawlResult.SiteStat crawlSingleSite(
            CrawlSiteRule rule,
            int targetCreatedCount,
            int candidateLimitPerSite,
            Set<String> seenArticleFingerprints) {
        CrawlResult.SiteStat stat = new CrawlResult.SiteStat();
        stat.setSiteName(rule.getSiteName());
        stat.setListUrl(rule.getListUrl());
        stat.setFetchedCount(0);
        stat.setParsedCount(0);
        stat.setCreatedCount(0);
        stat.setDeduplicatedCount(0);
        stat.setAiFailedCount(0);
        stat.setFailedCount(0);

        try {
            Document listDoc = fetchDocument(rule.getListUrl());
            List<String> articleUrls = normalizeAndDistinctUrls(rule.extractArticleUrls(listDoc, candidateLimitPerSite));
            stat.setFetchedCount(articleUrls.size());

            for (String articleUrl : articleUrls) {
                if (stat.getCreatedCount() >= targetCreatedCount || shouldStopBeforeNextItem()) {
                    break;
                }

                setCurrentSite(rule.getSiteName());
                setCurrentUrl(articleUrl);

                if (resourceService.existsBySourceUrl(articleUrl)) {
                    stat.setDeduplicatedCount(stat.getDeduplicatedCount() + 1);
                    continue;
                }

                try {
                    Document articleDoc = fetchDocument(articleUrl);
                    CrawledArticleRaw raw = rule.parseArticle(articleDoc, articleUrl);
                    CrawledArticleRaw cleaned = sanitizeRawArticle(raw, rule.getSiteName());
                    if (cleaned == null) {
                        stat.setFailedCount(stat.getFailedCount() + 1);
                        continue;
                    }

                    String articleFingerprint = buildArticleFingerprint(cleaned);
                    if (resourceService.existsByTitle(cleaned.getTitle())
                            || (!articleFingerprint.isBlank() && seenArticleFingerprints.contains(articleFingerprint))) {
                        stat.setDeduplicatedCount(stat.getDeduplicatedCount() + 1);
                        continue;
                    }

                    stat.setParsedCount(stat.getParsedCount() + 1);

                    CrawledArticleProcessed processed = processWithAi(cleaned);
                    if (!processed.isAiSuccess()) {
                        stat.setAiFailedCount(stat.getAiFailedCount() + 1);
                    }

                    Resource resource = toResource(processed);
                    Resource createdResource = resourceService.createResource(resource);
                    knowledgeIngestionService.ingestResource(createdResource);
                    if (!articleFingerprint.isBlank()) {
                        seenArticleFingerprints.add(articleFingerprint);
                    }
                    stat.setCreatedCount(stat.getCreatedCount() + 1);

                } catch (DuplicateKeyException duplicateKeyException) {
                    stat.setDeduplicatedCount(stat.getDeduplicatedCount() + 1);
                } catch (Exception itemEx) {
                    stat.setFailedCount(stat.getFailedCount() + 1);
                    log.warn("process article failed: site={}, url={}, err={}",
                            rule.getSiteName(), articleUrl, itemEx.getMessage());
                }
            }

        } catch (Exception e) {
            stat.setError(e.getMessage());
            stat.setFailedCount(stat.getFailedCount() + 1);
            log.error("crawl site failed: site={}, url={}", rule.getSiteName(), rule.getListUrl(), e);
        } finally {
            setCurrentUrl("");
        }

        return stat;
    }

    private Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .timeout(12000)
                .followRedirects(true)
                .get();
    }

    private Resource toResource(CrawledArticleProcessed processed) {
        Resource resource = new Resource();
        resource.setTitle(processed.getTitle());
        resource.setSource(processed.getSource());
        resource.setSourceUrl(processed.getSourceUrl());
        resource.setCategory(DEFAULT_CATEGORY);
        resource.setContent(processed.getExcerpt());
        resource.setIdeologySummary(processed.getIdeologySummary());
        resource.setTags(processed.getTagsJson());
        resource.setSyncStatus("SYNCED");
        return resource;
    }

    private CrawledArticleProcessed processWithAi(CrawledArticleRaw raw) {
        try {
            String systemPrompt = "You are a curriculum-ideology analysis assistant. "
                    + "Ignore webpage boilerplate such as login, subscribe, footer links, copyright, and navigation text. "
                    + "Return one JSON object only with fields: subject_summary, matched_ideology_categories, match_reason. "
                    + "subject_summary must be a concise factual abstract under 180 Chinese characters. "
                    + "matched_ideology_categories must be an array and may only use these values: "
                    + "工匠精神, 产业创新, 科技报国, 家国情怀, 责任担当, 绿色发展, 依法治理, 职业操守, 协同共治, 文化自信. "
                    + "match_reason must explain why the subject knowledge matches the selected ideology categories. "
                    + "Use the following corrected ideology categories exactly: "
                    + String.join(", ", ALLOWED_IDEOLOGY_TAGS) + ". "
                    + "If there is not enough evidence for ideology tagging, return an empty array.";

            String userPrompt = "Title: " + raw.getTitle() + "\n"
                    + "Source: " + raw.getSiteName() + "\n"
                    + "URL: " + raw.getSourceUrl() + "\n"
                    + "Content:\n" + truncate(raw.getContent(), CONTENT_FOR_AI_MAX_LENGTH);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            String aiResponse = aiIntelligenceService.chat(messages, systemPrompt);
            return parseAiResponse(raw, aiResponse);

        } catch (Exception e) {
            log.warn("ai processing failed, fallback used: title={}, err={}", raw.getTitle(), e.getMessage());
            return fallbackProcess(raw);
        }
    }

    private CrawledArticleProcessed parseAiResponse(CrawledArticleRaw raw, String aiResponse)
            throws JsonProcessingException {
        String jsonText = extractJsonObject(aiResponse);
        JsonNode root = objectMapper.readTree(jsonText);

        String summary = safeText(root.path("subject_summary"));
        String ideologySummary = safeText(root.path("match_reason"));

        String storedSummary = buildStoredSummary(raw.getTitle(), summary, buildExcerpt(raw.getContent()));
        String tagsJson = buildTagsJson(root.path("matched_ideology_categories"));

        return new CrawledArticleProcessed(
                raw.getTitle(),
                raw.getSiteName(),
                raw.getSourceUrl(),
                storedSummary,
                truncate(ideologySummary, EXCERPT_MAX_LENGTH),
                tagsJson,
                true);
    }

    private CrawledArticleProcessed fallbackProcess(CrawledArticleRaw raw) {
        List<String> fallbackTags = new ArrayList<>();
        fallbackTags.add("工匠精神");
        fallbackTags.add("责任担当");

        String tagsJson;
        try {
            tagsJson = objectMapper.writeValueAsString(fallbackTags);
        } catch (JsonProcessingException e) {
            tagsJson = "[\"工匠精神\",\"责任担当\"]";
        }

        tagsJson = EMPTY_TAGS_JSON;
        String fallbackSummary = buildStoredSummary(raw.getTitle(), "", buildExcerpt(raw.getContent()));

        return new CrawledArticleProcessed(
                raw.getTitle(),
                raw.getSiteName(),
                raw.getSourceUrl(),
                fallbackSummary,
                "",
                tagsJson,
                false);
    }

    private String buildTagsJson(JsonNode tagNode) throws JsonProcessingException {
        LinkedHashSet<String> tags = new LinkedHashSet<>();

        if (tagNode != null && tagNode.isArray()) {
            for (JsonNode node : tagNode) {
                String cleaned = sanitizeTag(safeText(node));
                if (!cleaned.isBlank()) {
                    tags.add(cleaned);
                }
            }
        } else {
            String tagText = sanitizeTag(safeText(tagNode));
            if (!tagText.isBlank()) {
                String[] parts = tagText.split("[,\\uFF0C\\u3001\\s]+");
                for (String part : parts) {
                    String cleaned = sanitizeTag(part);
                    if (!cleaned.isBlank()) {
                        tags.add(cleaned);
                    }
                }
            }
        }

        if (false && tags.isEmpty()) {
            tags.add("工匠精神");
            tags.add("责任担当");
        }

        List<String> finalTags = tags.stream()
                .map(this::canonicalizeIdeologyTag)
                .filter(tag -> !tag.isBlank())
                .limit(6)
                .toList();

        if (finalTags.isEmpty()) {
            return EMPTY_TAGS_JSON;
        }

        if (finalTags.isEmpty()) {
            return "[\"工匠精神\"]";
        }
        return objectMapper.writeValueAsString(finalTags);
    }

    private String sanitizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        String cleaned = normalizeWhitespace(tag)
                .replaceAll("^[#\\[\\(\\{\\u3010\\s]+", "")
                .replaceAll("[\\]\\)\\}\\u3011\\s]+$", "")
                .trim();

        if (cleaned.length() > 16) {
            return cleaned.substring(0, 16);
        }
        return cleaned;
    }

    private String canonicalizeIdeologyTag(String tag) {
        String cleaned = sanitizeTag(tag);
        if (cleaned.isBlank()) {
            return "";
        }

        for (String allowedTag : ALLOWED_IDEOLOGY_TAGS) {
            if (cleaned.equals(allowedTag) || cleaned.contains(allowedTag) || allowedTag.contains(cleaned)) {
                return allowedTag;
            }
        }
        return "";
    }

    private String buildStoredSummary(String title, String summary, String fallbackText) {
        String candidate = normalizeWhitespace(summary);
        if (candidate.isBlank()) {
            candidate = normalizeWhitespace(fallbackText);
        }
        if (candidate.isBlank()) {
            candidate = "Summary pending for: " + normalizeWhitespace(title);
        }
        return truncate(candidate, EXCERPT_MAX_LENGTH);
    }

    private String buildArticleFingerprint(CrawledArticleRaw raw) {
        if (raw == null) {
            return "";
        }

        String normalizedTitle = normalizeFingerprintText(raw.getTitle());
        String normalizedContent = normalizeFingerprintText(raw.getContent());
        if (normalizedContent.length() > 200) {
            normalizedContent = normalizedContent.substring(0, 200);
        }
        if (normalizedTitle.isBlank() && normalizedContent.isBlank()) {
            return "";
        }
        return normalizedTitle + "|" + normalizedContent;
    }

    private String normalizeFingerprintText(String text) {
        return normalizeWhitespace(text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private String extractJsonObject(String text) {
        if (text == null) {
            throw new IllegalArgumentException("empty ai response");
        }

        Matcher matcher = JSON_OBJECT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }

        throw new IllegalArgumentException("ai response does not contain json object");
    }

    private String safeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private CrawledArticleRaw sanitizeRawArticle(CrawledArticleRaw raw, String fallbackSiteName) {
        if (raw == null) {
            return null;
        }

        String normalizedUrl = normalizeArticleUrl(raw.getSourceUrl());
        String siteName = isBlank(raw.getSiteName()) ? fallbackSiteName : normalizeWhitespace(raw.getSiteName());
        String title = cleanTitle(raw.getTitle(), siteName);
        String content = cleanContent(raw.getContent());
        String publishTime = normalizeWhitespace(raw.getPublishTime());

        if (isBlank(normalizedUrl) || isBlank(title) || isBlank(content) || content.length() < MIN_CONTENT_LENGTH) {
            return null;
        }

        return new CrawledArticleRaw(siteName, title, normalizedUrl, publishTime, content);
    }

    private String cleanTitle(String title, String siteName) {
        String cleaned = normalizeWhitespace(title);
        if (cleaned.isBlank()) {
            return "";
        }

        cleaned = cleaned.replaceAll("^(\\u539F\\u6807\\u9898|\\u6765\\u6E90)[:\\uFF1A]\\s*", "");
        cleaned = cleaned.replaceAll("\\s*[\\-|\\uFF5C|_]\\s*"
                + "(\\u4EBA\\u6C11\\u7F51|\\u65B0\\u534E\\u7F51|"
                + "\\u4E2D\\u56FD\\u5171\\u4EA7\\u515A\\u65B0\\u95FB\\u7F51|"
                + "\\u5168\\u56FD\\u9AD8\\u6821\\u601D\\u60F3\\u653F\\u6CBB\\u5DE5\\u4F5C\\u7F51).*$", "");
        cleaned = cleaned.replaceAll("\\s*(\\u8D23\\u4EFB\\u7F16\\u8F91|\\u8D23\\u7F16)[:\\uFF1A]\\s*\\S+$", "");

        if (!isBlank(siteName) && cleaned.endsWith(siteName)) {
            cleaned = cleaned.substring(0, cleaned.length() - siteName.length()).trim();
        }

        cleaned = normalizeWhitespace(cleaned);
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(0, 200);
        }

        return cleaned;
    }

    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }

        String cleaned = content.replace('\u00A0', ' ');

        // drop obvious header/login/subscribe/footer fragments
        cleaned = cleaned.replaceAll("(\\u767B\\u5F55\\u4EBA\\u6C11\\u7F51\\u901A\\u884C\\u8BC1|\\u7ACB\\u5373\\u6CE8\\u518C|\\u53D6\\u6D88\\u8BA2\\u9605|\\u5DF2\\u6536\\u85CF|\\u5927\\u5B57\\u53F7|\\u70B9\\u51FB\\u64AD\\u62A5\\u672C\\u6587)", " ");
        cleaned = cleaned.replaceAll("(\\u4EBA\\u6C11\\u65E5\\u62A5\\u793E\\u6982\\u51B5|\\u5173\\u4E8E\\u4EBA\\u6C11\\u7F51|\\u62A5\\u793E\\u62DB\\u8058|\\u5E7F\\u544A\\u670D\\u52A1|\\u5408\\u4F5C\\u52A0\\u76DF|\\u4F9B\\u7A3F\\u670D\\u52A1|\\u6570\\u636E\\u670D\\u52A1|\\u7F51\\u7AD9\\u58F0\\u660E|\\u8054\\u7CFB\\u6211\\u4EEC|\\u8FDD\\u6CD5\\u548C\\u4E0D\\u826F\\u4FE1\\u606F).*$", " ");

        cleaned = cleaned.replaceAll("(\\u8D23\\u4EFB\\u7F16\\u8F91|\\u8D23\\u7F16)[:\\uFF1A]\\s*\\S+", " ");
        cleaned = cleaned.replaceAll("(\\u6253\\u5370|\\u5173\\u95ED\\u7A97\\u53E3|\\u8FD4\\u56DE\\u9876\\u90E8)", " ");
        cleaned = cleaned.replaceAll("(\\u5206\\u4EAB\\u5230|\\u5206\\u4EAB\\u81F3|\\u5FAE\\u4FE1\\u626B\\u4E00\\u626B).*$", " ");
        cleaned = cleaned.replaceAll("(\\u7248\\u6743\\u58F0\\u660E|\\u514D\\u8D23\\u58F0\\u660E|\\u8F6C\\u8F7D\\u987B\\u6CE8\\u660E).*$", " ");

        int footerIdx = cleaned.indexOf("\u4EBA\u6C11\u65E5\u62A5\u793E\u6982\u51B5");
        if (footerIdx > 0) {
            cleaned = cleaned.substring(0, footerIdx);
        }

        String[] parts = cleaned.split("[\\r\\n]+");
        if (parts.length <= 1) {
            parts = cleaned.split("(?<=\\u3002)");
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String line = normalizeWhitespace(part);
            if (line.length() < 10 || isNoiseLine(line)) {
                continue;
            }
            if (deduped.add(line)) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }

        String result = sb.length() > 0 ? sb.toString() : normalizeWhitespace(cleaned);
        return result.trim();
    }

    private boolean isNoiseLine(String line) {
        if (line == null || line.isBlank()) {
            return true;
        }
        return line.startsWith("\u8D23\u4EFB\u7F16\u8F91")
                || line.startsWith("\u8D23\u7F16")
                || line.startsWith("\u6765\u6E90")
                || line.startsWith("\u539F\u6807\u9898")
                || line.startsWith("\u5206\u4EAB\u5230")
                || line.startsWith("\u6253\u5370")
                || line.startsWith("\u8FD4\u56DE\u9876\u90E8")
                || line.contains("\u7248\u6743\u6240\u6709");
    }

    private String buildExcerpt(String content) {
        return truncate(normalizeWhitespace(content), EXCERPT_MAX_LENGTH);
    }

    private List<String> normalizeAndDistinctUrls(List<String> urls) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (urls != null) {
            for (String url : urls) {
                String normalized = normalizeArticleUrl(url);
                if (!normalized.isBlank()) {
                    unique.add(normalized);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private String normalizeArticleUrl(String url) {
        if (isBlank(url)) {
            return "";
        }

        String trimmed = url.trim();
        if (trimmed.startsWith("//")) {
            trimmed = "https:" + trimmed;
        }

        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (isBlank(scheme)) {
                return "";
            }

            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "";
            }

            String host = uri.getHost();
            if (isBlank(host)) {
                String authority = uri.getAuthority();
                if (!isBlank(authority)) {
                    String parsed = authority;
                    int atIdx = parsed.lastIndexOf('@');
                    if (atIdx >= 0) {
                        parsed = parsed.substring(atIdx + 1);
                    }
                    int portIdx = parsed.indexOf(':');
                    host = portIdx >= 0 ? parsed.substring(0, portIdx) : parsed;
                }
            }
            if (isBlank(host)) {
                return "";
            }

            host = host.toLowerCase(Locale.ROOT);

            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }

            String path = uri.getRawPath();
            if (isBlank(path)) {
                path = "/";
            }
            path = path.replaceAll("/{2,}", "/");
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String query = normalizeQuery(uri.getRawQuery());
            URI normalized = new URI(scheme, uri.getUserInfo(), host, port, path, query, null);
            return normalized.toString();
        } catch (Exception ignored) {
            int hashIndex = trimmed.indexOf('#');
            if (hashIndex >= 0) {
                trimmed = trimmed.substring(0, hashIndex);
            }
            if (trimmed.endsWith("/") && trimmed.length() > 1) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
    }

    private String normalizeQuery(String rawQuery) {
        if (isBlank(rawQuery)) {
            return null;
        }

        List<String> kept = new ArrayList<>();
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (isBlank(pair)) {
                continue;
            }

            String key;
            int eqIndex = pair.indexOf('=');
            if (eqIndex >= 0) {
                key = pair.substring(0, eqIndex);
            } else {
                key = pair;
            }

            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (shouldDropQueryKey(normalizedKey)) {
                continue;
            }

            kept.add(pair);
        }

        if (kept.isEmpty()) {
            return null;
        }

        Collections.sort(kept);
        return String.join("&", kept);
    }

    private boolean shouldDropQueryKey(String key) {
        if (isBlank(key)) {
            return false;
        }
        return key.startsWith("utm_") || TRACKING_QUERY_KEYS.contains(key);
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private void mergeResult(CrawlResult result, CrawlResult.SiteStat stat) {
        result.setTotalFetched(result.getTotalFetched() + stat.getFetchedCount());
        result.setTotalCreated(result.getTotalCreated() + stat.getCreatedCount());
        result.setTotalDeduplicated(result.getTotalDeduplicated() + stat.getDeduplicatedCount());
        result.setTotalAiFailed(result.getTotalAiFailed() + stat.getAiFailedCount());
        result.setTotalFailed(result.getTotalFailed() + stat.getFailedCount());
        currentResult = copyCrawlResult(result);
    }

    private CrawlResult initCrawlResult(LocalDateTime startTime) {
        CrawlResult result = new CrawlResult();
        result.setStartedAt(startTime);
        result.setLimitPerSite(TARGET_CREATED_PER_SITE);
        result.setTotalFetched(0);
        result.setTotalCreated(0);
        result.setTotalDeduplicated(0);
        result.setTotalAiFailed(0);
        result.setTotalFailed(0);
        result.setSiteStats(new ArrayList<>());
        return result;
    }

    private CrawlTaskStatus snapshotStatus() {
        CrawlTaskStatus status = new CrawlTaskStatus();
        status.setState(taskState.name());
        status.setStopRequested(stopRequested);
        status.setStartedAt(startedAt);
        status.setFinishedAt(finishedAt);
        status.setCurrentSite(currentSite);
        status.setCurrentUrl(currentUrl);
        status.setTargetCreatedPerSite(TARGET_CREATED_PER_SITE);
        status.setCandidateLimitPerSite(CANDIDATE_LIMIT_PER_SITE);
        status.setCurrentResult(copyCrawlResult(currentResult));
        status.setLastResult(copyCrawlResult(lastResult));
        status.setMessage(statusMessage);
        return status;
    }

    private CrawlResult copyCrawlResult(CrawlResult source) {
        if (source == null) {
            return null;
        }

        CrawlResult copied = new CrawlResult();
        copied.setStartedAt(source.getStartedAt());
        copied.setFinishedAt(source.getFinishedAt());
        copied.setLimitPerSite(source.getLimitPerSite());
        copied.setTotalFetched(source.getTotalFetched());
        copied.setTotalCreated(source.getTotalCreated());
        copied.setTotalDeduplicated(source.getTotalDeduplicated());
        copied.setTotalAiFailed(source.getTotalAiFailed());
        copied.setTotalFailed(source.getTotalFailed());
        copied.setDurationMs(source.getDurationMs());

        List<CrawlResult.SiteStat> copiedStats = new ArrayList<>();
        if (source.getSiteStats() != null) {
            for (CrawlResult.SiteStat stat : source.getSiteStats()) {
                copiedStats.add(new CrawlResult.SiteStat(
                        stat.getSiteName(),
                        stat.getListUrl(),
                        stat.getFetchedCount(),
                        stat.getParsedCount(),
                        stat.getCreatedCount(),
                        stat.getDeduplicatedCount(),
                        stat.getAiFailedCount(),
                        stat.getFailedCount(),
                        stat.getError()));
            }
        }
        copied.setSiteStats(copiedStats);

        return copied;
    }

    private boolean isTaskActive(CrawlTaskState state) {
        return state == CrawlTaskState.RUNNING || state == CrawlTaskState.STOP_REQUESTED;
    }

    private boolean shouldStopBeforeNextItem() {
        return stopRequested;
    }

    private void setCurrentSite(String siteName) {
        currentSite = siteName == null ? "" : siteName;
    }

    private void setCurrentUrl(String url) {
        currentUrl = url == null ? "" : url;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String normalized = normalizeWhitespace(text);
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    @PreDestroy
    public void shutdownExecutor() {
        crawlExecutor.shutdownNow();
    }
}


