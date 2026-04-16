package com.smartedu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.DocumentStructureDto;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.TeachingArtifactsDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.mapper.ParseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * AI 智能服务。
 *
 * <p>
 * 该服务统一负责：
 * 1. 对话类能力调用；
 * 2. 文档结构化解析；
 * 3. 思政价值提取；
 * 4. 后台任务的多提供商回退与熔断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiIntelligenceService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final double DEFAULT_TEMPERATURE = 0.7D;

    private final ParseTaskMapper parseTaskMapper;
    private final ObjectMapper objectMapper;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final AiPipelineJsonValidator pipelineJsonValidator;
    private final CourseService courseService;

    // 独立中转站 API 配置
    @Value("${ai.proxy.enabled:true}")
    private boolean proxyEnabled;

    @Value("${ai.proxy.label:default-proxy}")
    private String proxyLabel;

    @Value("${ai.proxy.api-key:}")
    private String proxyApiKey;

    @Value("${ai.proxy.base-url:https://codex.miaomiaocode.com/v1}")
    private String proxyBaseUrl;

    @Value("${ai.proxy.model:gpt-5-codex-mini}")
    private String proxyModel;

    @Value("${ai.proxy.timeout:120}")
    private int proxyTimeout;

    // DeepSeek 配置
    @Value("${ai.deepseek.enabled:true}")
    private boolean deepseekEnabled;

    @Value("${ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com/v1}")
    private String deepseekBaseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${ai.deepseek.timeout:120}")
    private int deepseekTimeout;

    // OpenAI 兼容 API 配置
    @Value("${ai.openai.enabled:false}")
    private boolean openaiEnabled;

    @Value("${ai.openai.label:openai}")
    private String openaiLabel;

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${ai.openai.timeout:120}")
    private int openaiTimeout;

    // Gemini 配置
    @Value("${ai.gemini.enabled:false}")
    private boolean geminiEnabled;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gemini.base-url:}")
    private String geminiBaseUrl;

    @Value("${ai.gemini.model:}")
    private String geminiModel;

    @Value("${ai.gemini.timeout:120}")
    private int geminiTimeout;

    // AI 路由与熔断配置
    @Value("${ai.routing.default-chat-provider:proxy}")
    private String defaultChatProvider;

    @Value("#{'${ai.routing.background-providers:proxy,deepseek,openai}'.split(',')}")
    private List<String> backgroundProviders;

    @Value("${ai.routing.failure-threshold:3}")
    private int failureThreshold;

    // 文档解析流水线配置
    @Value("${ai.pipeline.max-input-chars:5000}")
    private int maxInputChars;

    @Value("${ai.pipeline.max-knowledge-points:12}")
    private int maxKnowledgePoints;

    @Value("${ai.pipeline.max-ideology-matches-per-point:3}")
    private int maxIdeologyMatchesPerPoint;

    @Value("${ai.pipeline.max-questions:6}")
    private int maxQuestions;

    @Value("${ai.pipeline.schema-version:v1}")
    private String schemaVersion;

    @Value("${ai.pipeline.regenerate-retry:1}")
    private int regenerateRetry;

    // MinerU 配置保留，避免影响既有配置结构
    @Value("${mineru.api-key:}")
    private String mineruApiKey;

    @Value("${mineru.base-url:https://api.mineru.ai/v1}")
    private String mineruBaseUrl;

    @Value("${mineru.timeout:300}")
    private int mineruTimeout;

    /**
     * 连续失败计数用于在后台任务中自动熔断不稳定的 API。
     */
    private final Map<String, Integer> providerFailureCounts = new ConcurrentHashMap<>();

    /**
     * 一旦达到失败阈值，就会加入该集合并在后续自动跳过。
     */
    private final Set<String> disabledProviders = ConcurrentHashMap.newKeySet();

    /**
     * 后台任务默认入口。
     *
     * <p>
     * 该入口不会暴露给前端选择，而是按配置的顺序自动依次尝试。
     */
    public String chat(List<Map<String, String>> messages, String systemPrompt) {
        return callWithProviderChain(messages, systemPrompt, buildBackgroundProviderChain());
    }

    /**
     * 对话场景入口。
     *
     * <p>
     * 当前端传入 default 时，优先使用中转站 API；
     * 如果该节点已熔断，则继续按后台顺序回退，前端无需感知后台切换逻辑。
     */
    public String chat(List<Map<String, String>> messages, String systemPrompt, String preferredProvider) {
        return callWithProviderChain(messages, systemPrompt, buildPreferredProviderChain(preferredProvider));
    }

    /**
     * 调用独立中转站 API。
     */
    private String callProxy(List<Map<String, String>> messages, String systemPrompt) {
        return callCompatibleChat(
                proxyBaseUrl,
                proxyApiKey,
                proxyModel,
                proxyTimeout,
                messages,
                systemPrompt,
                proxyLabel);
    }

    /**
     * 调用 DeepSeek API。
     */
    private String callDeepSeek(List<Map<String, String>> messages, String systemPrompt) {
        return callCompatibleChat(
                deepseekBaseUrl,
                deepseekApiKey,
                deepseekModel,
                deepseekTimeout,
                messages,
                systemPrompt,
                "deepseek");
    }

    /**
     * 调用 OpenAI 兼容 API。
     */
    private String callOpenAiCompatible(List<Map<String, String>> messages, String systemPrompt) {
        return callCompatibleChat(
                openaiBaseUrl,
                openaiApiKey,
                openaiModel,
                openaiTimeout,
                messages,
                systemPrompt,
                openaiLabel);
    }

    /**
     * Gemini 仍沿用原有预留实现。
     *
     * <p>
     * 为避免引入额外协议差异，当前保持回退到 OpenAI 兼容接口。
     */
    private String callGemini(List<Map<String, String>> messages, String systemPrompt) {
        log.warn("Gemini provider is not implemented, fallback to OpenAI-compatible provider");
        return callOpenAiCompatible(messages, systemPrompt);
    }

    /**
     * 统一兼容 Chat Completions 协议的调用。
     */
    private String callCompatibleChat(
            String apiBase,
            String apiKey,
            String model,
            int timeout,
            List<Map<String, String>> messages,
            String systemPrompt,
            String providerLabel) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .build();

            List<Map<String, String>> allMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                allMessages.add(sysMsg);
            }
            allMessages.addAll(messages);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", allMessages);
            requestBody.put("temperature", DEFAULT_TEMPERATURE);
            requestBody.put("max_tokens", DEFAULT_MAX_TOKENS);

            String endpoint = buildChatCompletionUrl(apiBase);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    log.error("{} API call failed: status={}, body={}", providerLabel, response.code(), responseBody);
                    throw new RuntimeException("AI service call failed: " + response.code());
                }

                JsonNode jsonNode = objectMapper.readTree(responseBody);
                String content = jsonNode.path("choices").path(0).path("message").path("content").asText();
                if (content == null || content.isBlank()) {
                    throw new RuntimeException("AI service returned empty content");
                }
                return content;
            }
        } catch (IOException e) {
            log.error("{} API call exception", providerLabel, e);
            throw new RuntimeException("AI service call failed: " + e.getMessage(), e);
        }
    }

    private String buildChatCompletionUrl(String apiBase) {
        if (apiBase == null || apiBase.isBlank()) {
            throw new RuntimeException("AI service base-url is missing");
        }

        String normalized = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    /**
     * 后台任务默认链路。
     */
    private List<String> buildBackgroundProviderChain() {
        List<String> providers = deduplicateProviders(backgroundProviders);
        if (providers.isEmpty()) {
            providers.add(normalizeProviderKey(defaultChatProvider));
        }
        return providers;
    }

    /**
     * 对话场景链路。
     */
    private List<String> buildPreferredProviderChain(String preferredProvider) {
        String normalized = normalizeProviderKey(preferredProvider);
        if (normalized.isBlank() || "default".equals(normalized)) {
            List<String> providers = new ArrayList<>();
            providers.add(defaultChatProvider);
            providers.addAll(backgroundProviders);
            return deduplicateProviders(providers);
        }

        List<String> providers = new ArrayList<>();
        providers.add(normalized);
        return providers;
    }

    /**
     * 统一按链路尝试提供商。
     */
    private String callWithProviderChain(
            List<Map<String, String>> messages,
            String systemPrompt,
            List<String> providerChain) {
        if (providerChain == null || providerChain.isEmpty()) {
            throw new RuntimeException("No AI provider configured");
        }

        List<String> errors = new ArrayList<>();
        for (String providerKey : providerChain) {
            ProviderSettings providerSettings = getProviderSettings(providerKey);
            if (!providerSettings.enabled()) {
                errors.add(providerKey + " disabled by config");
                continue;
            }
            if (disabledProviders.contains(providerSettings.key())) {
                errors.add(providerKey + " disabled by circuit breaker");
                continue;
            }

            try {
                String response = callProvider(providerSettings, messages, systemPrompt);
                markProviderSuccess(providerSettings.key());
                return response;
            } catch (RuntimeException ex) {
                markProviderFailure(providerSettings.key(), ex);
                errors.add(providerKey + ": " + ex.getMessage());
            }
        }

        throw new RuntimeException("All configured AI providers failed: " + String.join(" | ", errors));
    }

    /**
     * 根据提供商标识装配配置。
     */
    private ProviderSettings getProviderSettings(String providerKey) {
        String normalizedKey = normalizeProviderKey(providerKey);
        return switch (normalizedKey) {
            case "default", "proxy" -> new ProviderSettings(
                    "proxy",
                    proxyLabel,
                    proxyEnabled,
                    proxyApiKey,
                    proxyBaseUrl,
                    proxyModel,
                    proxyTimeout);
            case "deepseek" -> new ProviderSettings(
                    "deepseek",
                    "deepseek",
                    deepseekEnabled,
                    deepseekApiKey,
                    deepseekBaseUrl,
                    deepseekModel,
                    deepseekTimeout);
            case "openai" -> new ProviderSettings(
                    "openai",
                    openaiLabel,
                    openaiEnabled,
                    openaiApiKey,
                    openaiBaseUrl,
                    openaiModel,
                    openaiTimeout);
            case "gemini" -> new ProviderSettings(
                    "gemini",
                    "gemini",
                    geminiEnabled,
                    geminiApiKey,
                    geminiBaseUrl,
                    geminiModel,
                    geminiTimeout);
            default -> throw new RuntimeException("Unsupported AI provider: " + providerKey);
        };
    }

    /**
     * 统一分发到不同提供商的真实调用实现。
     */
    private String callProvider(
            ProviderSettings providerSettings,
            List<Map<String, String>> messages,
            String systemPrompt) {
        validateProviderSettings(providerSettings);

        return switch (providerSettings.key()) {
            case "proxy" -> callProxy(messages, systemPrompt);
            case "deepseek" -> callDeepSeek(messages, systemPrompt);
            case "openai" -> callOpenAiCompatible(messages, systemPrompt);
            case "gemini" -> callGemini(messages, systemPrompt);
            default -> throw new RuntimeException("Unsupported AI provider: " + providerSettings.key());
        };
    }

    /**
     * 在真正发起请求前做一次最小校验，避免空配置反复进入重试链路。
     */
    private void validateProviderSettings(ProviderSettings providerSettings) {
        if ("gemini".equals(providerSettings.key())) {
            return;
        }

        if (providerSettings.apiKey() == null || providerSettings.apiKey().isBlank()) {
            throw new RuntimeException(providerSettings.label() + " api-key is missing");
        }
        if (providerSettings.apiBase() == null || providerSettings.apiBase().isBlank()) {
            throw new RuntimeException(providerSettings.label() + " base-url is missing");
        }
        if (providerSettings.model() == null || providerSettings.model().isBlank()) {
            throw new RuntimeException(providerSettings.label() + " model is missing");
        }
    }

    /**
     * 成功一次即重置失败计数，防止短时抖动导致节点长期处于降级状态。
     */
    private void markProviderSuccess(String providerKey) {
        providerFailureCounts.remove(providerKey);
        disabledProviders.remove(providerKey);
    }

    /**
     * 连续失败达到阈值后直接熔断。
     */
    private void markProviderFailure(String providerKey, RuntimeException ex) {
        int safeThreshold = Math.max(failureThreshold, 1);
        int failures = providerFailureCounts.merge(providerKey, 1, Integer::sum);
        if (failures >= safeThreshold) {
            disabledProviders.add(providerKey);
            log.error("AI provider disabled after consecutive failures: provider={}, failures={}", providerKey, failures, ex);
            return;
        }

        log.warn("AI provider call failed: provider={}, failures={}", providerKey, failures, ex);
    }

    private String normalizeProviderKey(String providerKey) {
        if (providerKey == null) {
            return "";
        }
        return providerKey.trim().toLowerCase();
    }

    /**
     * 去重时保留顺序，确保回退链路可控。
     */
    private List<String> deduplicateProviders(List<String> providers) {
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        if (providers == null) {
            return new ArrayList<>();
        }

        for (String provider : providers) {
            String normalized = normalizeProviderKey(provider);
            if (!normalized.isBlank()) {
                deduplicated.add(normalized);
            }
        }
        return new ArrayList<>(deduplicated);
    }

    /**
     * 提取专业内容中的思政价值。
     */
    public String extractIdeologicalValue(String technicalContent, String subject) {
        String systemPrompt = "You are an expert in curriculum ideology integration. "
                + "Analyze the given subject content and provide teaching suggestions from the perspectives of craftsmanship, national responsibility, professional ethics, and critical thinking.";

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "Analyze the ideological value in the following subject content for \"" + subject + "\" and provide classroom integration suggestions:\n\n" + technicalContent);
        messages.add(userMsg);

        return chat(messages, systemPrompt);
    }

    /**
     * 异步解析文档并完成结构化流水线。
     */
    @Async
    public void processDocumentAsync(Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("Parse task not found: {}", taskId);
            return;
        }

        try {
            runPipeline(task, true, 1);
            log.info("Document parse completed: taskId={}", taskId);
        } catch (Exception e) {
            log.error("Document parse failed: taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            parseTaskMapper.updateById(task);
        }
    }

    /**
     * 基于已有解析结果重新生成结构化教学内容。
     */
    public PipelineResultDto regenerateTask(Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("Parse task not found");
        }
        if (task.getParsedContent() == null || task.getParsedContent().isBlank()) {
            throw new RuntimeException("Task has no parsed content to regenerate");
        }

        try {
            updateTaskProgress(task, "ANALYZING", 55, "Regenerating teaching pipeline...");
            return runPipeline(task, false, Math.max(1, regenerateRetry + 1));
        } catch (RuntimeException ex) {
            task.setStatus("FAILED");
            task.setErrorMessage(ex.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            parseTaskMapper.updateById(task);
            throw ex;
        }
    }

    /**
     * 读取结构化结果，供 result-detail 接口使用。
     */
    public PipelineResultDto getPipelineResult(Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("Parse task not found");
        }
        return parsePipelineResult(task.getAiAnalysis(), task.getParsedContent());
    }

    private PipelineResultDto runPipeline(ParseTask task, boolean includeDocumentParse, int pipelineAttempts) {
        String rawContent = readFileContent(task.getFilePath());
        String fileName = extractFileName(task.getFilePath());
        String courseContext = buildCourseContext(task.getCourseId());
        List<String> warnings = new ArrayList<>();
        boolean inferred = false;

        DocumentStructureDto structure;
        if (includeDocumentParse) {
            updateTaskProgress(task, "PARSING", 20, "Parsing document structure...");
            StageResult<DocumentStructureDto> parseResult = parseDocumentStructure(rawContent, fileName, courseContext);
            structure = parseResult.data();
            warnings.addAll(parseResult.warnings());
            inferred = inferred || parseResult.inferred();
            task.setParsedContent(writeJsonSafely(structure));
            task.setUpdatedAt(LocalDateTime.now());
            parseTaskMapper.updateById(task);
        } else {
            try {
                structure = objectMapper.readValue(task.getParsedContent(), DocumentStructureDto.class);
            } catch (Exception ex) {
                throw new RuntimeException("Existing parsed content is invalid JSON", ex);
            }
            warnings.add("Document structure reused from existing parsed content.");
        }

        updateTaskProgress(task, "PARSING", 40, "Extracting knowledge points...");
        StageResult<List<KnowledgePointDto>> knowledgeResult =
                extractKnowledgePoints(structure, truncateInput(rawContent), fileName, courseContext);
        List<KnowledgePointDto> knowledgePoints = knowledgeResult.data();
        warnings.addAll(knowledgeResult.warnings());
        inferred = inferred || knowledgeResult.inferred();

        updateTaskProgress(task, "ANALYZING", 60, "Matching ideology elements...");
        StageResult<List<IdeologyMatchDto>> ideologyResult =
                matchIdeologyElements(structure, knowledgePoints, courseContext);
        List<IdeologyMatchDto> ideologyMatches = ideologyResult.data();
        warnings.addAll(ideologyResult.warnings());
        inferred = inferred || ideologyResult.inferred();

        updateTaskProgress(task, "ANALYZING", 80, "Generating teaching artifacts...");
        StageResult<TeachingArtifactsDto> artifactResult =
                generateTeachingArtifacts(structure, knowledgePoints, ideologyMatches, courseContext);
        TeachingArtifactsDto artifacts = artifactResult.data();
        warnings.addAll(artifactResult.warnings());
        inferred = inferred || artifactResult.inferred();

        PipelineResultDto pipelineResult = new PipelineResultDto();
        pipelineResult.setDocumentStructure(structure);
        pipelineResult.setKnowledgePoints(knowledgePoints);
        pipelineResult.setIdeologyMatches(ideologyMatches);
        pipelineResult.setTeachingArtifacts(artifacts);
        pipelineResult.setWarnings(new ArrayList<>(warnings));
        pipelineResult.setInferred(inferred);
        pipelineResult.setSchemaVersion(schemaVersion);

        task.setAiAnalysis(writeJsonSafely(pipelineResult));
        task.setUpdatedAt(LocalDateTime.now());
        parseTaskMapper.updateById(task);

        updateTaskProgress(task, "ANALYZING", 90, "Syncing knowledge points and sources...");
        knowledgeIngestionService.ingestParseTask(task);

        task.setStatus("COMPLETED");
        task.setProgress(100);
        task.setCurrentStep("Completed");
        task.setCompletedAt(LocalDateTime.now());
        parseTaskMapper.updateById(task);

        // regenerate-retry 用于重新生成场景；首次流程固定为 1 次重试。
        if (pipelineAttempts > 1 && inferred) {
            for (int i = 1; i < pipelineAttempts; i++) {
                PipelineResultDto retryResult = tryRebuildFromParsedContent(task, structure, rawContent, courseContext);
                if (!retryResult.isInferred()) {
                    return retryResult;
                }
            }
        }

        return pipelineResult;
    }

    private PipelineResultDto tryRebuildFromParsedContent(
            ParseTask task,
            DocumentStructureDto structure,
            String rawContent,
            String courseContext) {
        List<String> warnings = new ArrayList<>();
        StageResult<List<KnowledgePointDto>> knowledgeResult =
                extractKnowledgePoints(structure, truncateInput(rawContent), extractFileName(task.getFilePath()), courseContext);
        StageResult<List<IdeologyMatchDto>> ideologyResult =
                matchIdeologyElements(structure, knowledgeResult.data(), courseContext);
        StageResult<TeachingArtifactsDto> artifactResult =
                generateTeachingArtifacts(structure, knowledgeResult.data(), ideologyResult.data(), courseContext);
        boolean inferred = knowledgeResult.inferred() || ideologyResult.inferred() || artifactResult.inferred();

        warnings.addAll(knowledgeResult.warnings());
        warnings.addAll(ideologyResult.warnings());
        warnings.addAll(artifactResult.warnings());

        PipelineResultDto pipelineResult = new PipelineResultDto();
        pipelineResult.setDocumentStructure(structure);
        pipelineResult.setKnowledgePoints(knowledgeResult.data());
        pipelineResult.setIdeologyMatches(ideologyResult.data());
        pipelineResult.setTeachingArtifacts(artifactResult.data());
        pipelineResult.setWarnings(warnings);
        pipelineResult.setInferred(inferred);
        pipelineResult.setSchemaVersion(schemaVersion);

        task.setAiAnalysis(writeJsonSafely(pipelineResult));
        task.setUpdatedAt(LocalDateTime.now());
        parseTaskMapper.updateById(task);
        return pipelineResult;
    }

    /**
     * 更新任务进度。
     */
    private void updateTaskProgress(ParseTask task, String status, int progress, String step) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setCurrentStep(step);
        task.setUpdatedAt(LocalDateTime.now());
        parseTaskMapper.updateById(task);

        try {
            Thread.sleep(350);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private StageResult<DocumentStructureDto> parseDocumentStructure(
            String rawContent,
            String fileName,
            String courseContext) {
        String systemPrompt = """
                You are a strict teaching document parser.
                Return JSON object only.
                Allowed keys: title, documentType, overview, chapterOutline, teachingFocus.
                Required keys: title, documentType, overview, chapterOutline, teachingFocus.
                documentType must be one of: TEXTBOOK, OUTLINE, PAPER, UNKNOWN.
                chapterOutline is an array of short strings.
                teachingFocus is an array of short strings.
                Do not include extra keys or markdown.
                """;
        String userPrompt = """
                Parse the document into the required JSON schema.
                Input file name: %s
                Course context:
                %s
                Input content:
                %s
                """.formatted(fileName, safeCourseContext(courseContext), truncateInput(rawContent));

        return executeStage(
                "document-structure",
                systemPrompt,
                userPrompt,
                this::validateDocumentStructure,
                this::buildDocumentStructureFallback);
    }

    private StageResult<List<KnowledgePointDto>> extractKnowledgePoints(
            DocumentStructureDto structure,
            String rawContent,
            String fileName,
            String courseContext) {
        String systemPrompt = """
                You are a strict knowledge point extractor.
                Return JSON object only.
                Allowed keys: knowledgePoints.
                Required keys: knowledgePoints.
                knowledgePoints is an array.
                Each item keys: pointName, definition, chapter, importance, evidenceSnippet.
                importance must be HIGH or MEDIUM or LOW.
                No extra keys and no markdown.
                """;
        String userPrompt = """
                Extract concise knowledge points based on parsed structure and content.
                Max points: %d
                File name: %s
                Course context:
                %s
                Structure JSON:
                %s
                Content:
                %s
                """.formatted(
                maxKnowledgePoints,
                fileName,
                safeCourseContext(courseContext),
                writeJsonSafely(structure),
                rawContent);

        return executeStage(
                "knowledge-point-extraction",
                systemPrompt,
                userPrompt,
                this::validateKnowledgePoints,
                this::buildKnowledgePointFallback);
    }

    private StageResult<List<IdeologyMatchDto>> matchIdeologyElements(
            DocumentStructureDto structure,
            List<KnowledgePointDto> knowledgePoints,
            String courseContext) {
        String systemPrompt = """
                You are a strict curriculum ideology matcher.
                Return JSON object only.
                Allowed keys: ideologyMatches.
                Required keys: ideologyMatches.
                ideologyMatches is an array.
                Each item keys: knowledgePointName, ideologyElement, matchReason, confidence.
                confidence must be integer between 0 and 100.
                ideologyElement must be one short phrase.
                No extra keys and no markdown.
                """;
        String userPrompt = """
                Match ideology elements for each knowledge point.
                Max matches per point: %d
                Course context:
                %s
                Parsed structure:
                %s
                Knowledge points:
                %s
                """.formatted(
                maxIdeologyMatchesPerPoint,
                safeCourseContext(courseContext),
                writeJsonSafely(structure),
                writeJsonSafely(knowledgePoints));

        return executeStage(
                "ideology-matching",
                systemPrompt,
                userPrompt,
                this::validateIdeologyMatches,
                ignored -> buildIdeologyMatchFallback(knowledgePoints));
    }

    private StageResult<TeachingArtifactsDto> generateTeachingArtifacts(
            DocumentStructureDto structure,
            List<KnowledgePointDto> knowledgePoints,
            List<IdeologyMatchDto> ideologyMatches,
            String courseContext) {
        String systemPrompt = """
                You are a strict teaching material generator.
                Return JSON object only.
                Allowed keys: lectureNotes, cases, questions.
                Required keys: lectureNotes, cases, questions.
                cases is an array of short case texts.
                questions is an array.
                Each question keys: stem, referenceAnswer, scoringPoints.
                scoringPoints is an array of short strings.
                No extra keys and no markdown.
                """;
        String userPrompt = """
                Generate lecture notes, cases, and exam questions.
                Max questions: %d
                Course context:
                %s
                Structure:
                %s
                Knowledge points:
                %s
                Ideology matches:
                %s
                """.formatted(
                maxQuestions,
                safeCourseContext(courseContext),
                writeJsonSafely(structure),
                writeJsonSafely(knowledgePoints),
                writeJsonSafely(ideologyMatches));

        return executeStage(
                "teaching-artifact-generation",
                systemPrompt,
                userPrompt,
                this::validateTeachingArtifacts,
                this::buildTeachingArtifactsFallback);
    }

    private <T> StageResult<T> executeStage(
            String stageName,
            String systemPrompt,
            String userPrompt,
            StageValidator<T> validator,
            StageFallback<T> fallbackSupplier) {
        List<String> warnings = new ArrayList<>();
        RuntimeException lastException = null;
        int attempts = 2;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String result = callStructuredPrompt(systemPrompt, userPrompt);
                T validated = validator.validate(result);
                return new StageResult<>(validated, warnings, false);
            } catch (RuntimeException ex) {
                lastException = ex;
                warnings.add(stageName + " attempt " + attempt + " failed: " + ex.getMessage());
            }
        }

        T fallback = fallbackSupplier.fallback(lastException);
        warnings.add(stageName + " fallback used.");
        return new StageResult<>(fallback, warnings, true);
    }

    private String callStructuredPrompt(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        return chat(messages, systemPrompt);
    }

    private DocumentStructureDto validateDocumentStructure(String rawJson) {
        JsonNode node = pipelineJsonValidator.parseObject(
                rawJson,
                List.of("title", "documentType", "overview", "chapterOutline", "teachingFocus"),
                List.of("title", "documentType", "overview", "chapterOutline", "teachingFocus"));
        pipelineJsonValidator.validateStringLength(node, "title", 120);
        pipelineJsonValidator.validateStringLength(node, "overview", 1200);
        pipelineJsonValidator.validateArraySize(node, "chapterOutline", 20);
        pipelineJsonValidator.validateArraySize(node, "teachingFocus", 20);

        String documentType = safeText(node.get("documentType"));
        if (!List.of("TEXTBOOK", "OUTLINE", "PAPER", "UNKNOWN").contains(documentType)) {
            throw new IllegalArgumentException("Invalid documentType: " + documentType);
        }
        return objectMapper.convertValue(node, DocumentStructureDto.class);
    }

    private List<KnowledgePointDto> validateKnowledgePoints(String rawJson) {
        JsonNode node = pipelineJsonValidator.parseObject(
                rawJson,
                List.of("knowledgePoints"),
                List.of("knowledgePoints"));
        pipelineJsonValidator.validateArraySize(node, "knowledgePoints", maxKnowledgePoints);

        JsonNode points = node.get("knowledgePoints");
        if (points == null || !points.isArray()) {
            throw new IllegalArgumentException("knowledgePoints must be an array");
        }

        List<KnowledgePointDto> result = new ArrayList<>();
        for (JsonNode pointNode : points) {
            pipelineJsonValidator.parseObject(
                    pointNode.toString(),
                    List.of("pointName", "definition", "chapter", "importance", "evidenceSnippet"),
                    List.of("pointName", "definition", "chapter", "importance", "evidenceSnippet"));

            String importance = safeText(pointNode.get("importance"));
            if (!List.of("HIGH", "MEDIUM", "LOW").contains(importance)) {
                throw new IllegalArgumentException("Invalid importance: " + importance);
            }

            KnowledgePointDto dto = objectMapper.convertValue(pointNode, KnowledgePointDto.class);
            dto.setPointName(trimToLength(dto.getPointName(), 120));
            dto.setDefinition(trimToLength(dto.getDefinition(), 500));
            dto.setChapter(trimToLength(dto.getChapter(), 120));
            dto.setEvidenceSnippet(trimToLength(dto.getEvidenceSnippet(), 300));
            result.add(dto);
        }
        return result;
    }

    private List<IdeologyMatchDto> validateIdeologyMatches(String rawJson) {
        JsonNode node = pipelineJsonValidator.parseObject(
                rawJson,
                List.of("ideologyMatches"),
                List.of("ideologyMatches"));

        JsonNode matchesNode = node.get("ideologyMatches");
        if (matchesNode == null || !matchesNode.isArray()) {
            throw new IllegalArgumentException("ideologyMatches must be an array");
        }
        pipelineJsonValidator.validateArraySize(node, "ideologyMatches", Math.max(1, maxKnowledgePoints * maxIdeologyMatchesPerPoint));

        List<IdeologyMatchDto> result = new ArrayList<>();
        for (JsonNode matchNode : matchesNode) {
            pipelineJsonValidator.parseObject(
                    matchNode.toString(),
                    List.of("knowledgePointName", "ideologyElement", "matchReason", "confidence"),
                    List.of("knowledgePointName", "ideologyElement", "matchReason", "confidence"));

            int confidence = matchNode.get("confidence").asInt(-1);
            if (confidence < 0 || confidence > 100) {
                throw new IllegalArgumentException("confidence must be in [0, 100]");
            }

            IdeologyMatchDto dto = objectMapper.convertValue(matchNode, IdeologyMatchDto.class);
            dto.setKnowledgePointName(trimToLength(dto.getKnowledgePointName(), 120));
            dto.setIdeologyElement(trimToLength(dto.getIdeologyElement(), 120));
            dto.setMatchReason(trimToLength(dto.getMatchReason(), 500));
            result.add(dto);
        }
        return result;
    }

    private TeachingArtifactsDto validateTeachingArtifacts(String rawJson) {
        JsonNode node = pipelineJsonValidator.parseObject(
                rawJson,
                List.of("lectureNotes", "cases", "questions"),
                List.of("lectureNotes", "cases", "questions"));
        pipelineJsonValidator.validateStringLength(node, "lectureNotes", 5000);
        pipelineJsonValidator.validateArraySize(node, "cases", 12);
        pipelineJsonValidator.validateArraySize(node, "questions", maxQuestions);

        JsonNode questionsNode = node.get("questions");
        if (questionsNode == null || !questionsNode.isArray()) {
            throw new IllegalArgumentException("questions must be an array");
        }
        for (JsonNode questionNode : questionsNode) {
            pipelineJsonValidator.parseObject(
                    questionNode.toString(),
                    List.of("stem", "referenceAnswer", "scoringPoints"),
                    List.of("stem", "referenceAnswer", "scoringPoints"));
            if (questionNode.get("scoringPoints") == null || !questionNode.get("scoringPoints").isArray()) {
                throw new IllegalArgumentException("scoringPoints must be an array");
            }
        }

        TeachingArtifactsDto dto = objectMapper.convertValue(node, TeachingArtifactsDto.class);
        dto.setLectureNotes(trimToLength(dto.getLectureNotes(), 5000));
        if (dto.getCases() == null) {
            dto.setCases(new ArrayList<>());
        }
        if (dto.getQuestions() == null) {
            dto.setQuestions(new ArrayList<>());
        }
        return dto;
    }

    private DocumentStructureDto buildDocumentStructureFallback(RuntimeException ex) {
        DocumentStructureDto fallback = new DocumentStructureDto();
        fallback.setTitle("Fallback Document");
        fallback.setDocumentType("UNKNOWN");
        fallback.setOverview("Document structure parser fallback result.");
        fallback.setChapterOutline(List.of("Chapter extraction pending"));
        fallback.setTeachingFocus(List.of("Please retry pipeline regeneration after provider recovery."));
        return fallback;
    }

    private List<KnowledgePointDto> buildKnowledgePointFallback(RuntimeException ex) {
        List<KnowledgePointDto> fallback = new ArrayList<>();
        fallback.add(new KnowledgePointDto(
                "Pending Knowledge Point",
                "Knowledge extraction fallback result.",
                "Unknown Chapter",
                "MEDIUM",
                "No reliable evidence snippet from provider."));
        return fallback;
    }

    private List<IdeologyMatchDto> buildIdeologyMatchFallback(List<KnowledgePointDto> points) {
        List<IdeologyMatchDto> fallback = new ArrayList<>();
        for (KnowledgePointDto point : points) {
            fallback.add(new IdeologyMatchDto(
                    point.getPointName(),
                    "Craftsmanship Spirit",
                    "Fallback ideology mapping due to provider instability.",
                    55));
        }
        return fallback;
    }

    private TeachingArtifactsDto buildTeachingArtifactsFallback(RuntimeException ex) {
        TeachingArtifactsDto fallback = new TeachingArtifactsDto();
        fallback.setLectureNotes("Teaching artifact generation fallback result.");
        fallback.setCases(List.of("Case generation is pending due to provider fallback."));
        fallback.setQuestions(List.of(
                new TeachingArtifactsDto.QuestionDto(
                        "Explain the key concept and its ideology relevance.",
                        "Reference answer pending provider recovery.",
                        List.of("Concept accuracy", "Ideology relevance", "Teaching expression"))));
        return fallback;
    }

    private PipelineResultDto parsePipelineResult(String aiAnalysis, String parsedContent) {
        if (aiAnalysis == null || aiAnalysis.isBlank()) {
            PipelineResultDto emptyResult = new PipelineResultDto();
            emptyResult.setSchemaVersion(schemaVersion);
            emptyResult.setInferred(true);
            emptyResult.setWarnings(new ArrayList<>(List.of("Pipeline result is empty.")));
            if (parsedContent != null && !parsedContent.isBlank()) {
                try {
                    emptyResult.setDocumentStructure(objectMapper.readValue(parsedContent, DocumentStructureDto.class));
                } catch (Exception ex) {
                    emptyResult.getWarnings().add("Parsed content exists but failed to deserialize.");
                }
            }
            return emptyResult;
        }

        try {
            PipelineResultDto result = objectMapper.readValue(aiAnalysis, PipelineResultDto.class);
            if (result.getSchemaVersion() == null || result.getSchemaVersion().isBlank()) {
                result.setSchemaVersion(schemaVersion);
            }
            if (result.getWarnings() == null) {
                result.setWarnings(new ArrayList<>());
            }
            if (result.getKnowledgePoints() == null) {
                result.setKnowledgePoints(new ArrayList<>());
            }
            if (result.getIdeologyMatches() == null) {
                result.setIdeologyMatches(new ArrayList<>());
            }
            if (result.getDocumentStructure() == null && parsedContent != null && !parsedContent.isBlank()) {
                result.setDocumentStructure(objectMapper.readValue(parsedContent, DocumentStructureDto.class));
            }
            return result;
        } catch (Exception ex) {
            throw new RuntimeException("Pipeline result JSON is invalid", ex);
        }
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize pipeline payload", ex);
        }
    }

    private String truncateInput(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "";
        }
        String trimmed = rawContent.trim();
        if (trimmed.length() <= maxInputChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxInputChars);
    }

    /**
     * Build optional course-aware context for teaching pipeline prompts.
     */
    private String buildCourseContext(Long courseId) {
        if (courseId == null) {
            return "";
        }
        try {
            List<KnowledgeNodeView> nodes = courseService.getCourseKnowledgePoints(courseId);
            if (nodes == null || nodes.isEmpty()) {
                return "No course knowledge points found.";
            }
            StringBuilder context = new StringBuilder();
            context.append("courseId=").append(courseId).append('\n');
            int count = 0;
            for (KnowledgeNodeView node : nodes) {
                if (count >= 12) {
                    break;
                }
                context.append("- ")
                        .append(trimToLength(node.getName(), 120))
                        .append(" | definition: ")
                        .append(trimToLength(node.getTechnicalDefinition(), 220))
                        .append(" | ideology: ")
                        .append(trimToLength(node.getIdeologicalValue(), 220))
                        .append('\n');
                count++;
            }
            return context.toString();
        } catch (Exception ex) {
            log.warn("Failed to build course context, fallback to empty context: courseId={}", courseId, ex);
            return "";
        }
    }

    private String safeCourseContext(String courseContext) {
        if (courseContext == null || courseContext.isBlank()) {
            return "No explicit course context.";
        }
        return truncateInput(courseContext);
    }

    private String extractFileName(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "unknown-file";
        }
        if (filePath.contains("/")) {
            return filePath.substring(filePath.lastIndexOf('/') + 1);
        }
        if (filePath.contains("\\")) {
            return filePath.substring(filePath.lastIndexOf('\\') + 1);
        }
        return filePath;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private String safeText(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return "";
        }
        return node.asText().trim();
    }

    /**
     * 尽量读取文本内容，失败时允许回退到文件名推断。
     */
    private String readFileContent(String filePath) {
        java.io.File file = new java.io.File(filePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("File path is invalid: {}", filePath);
            return null;
        }

        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of(filePath),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception utf8Ex) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(filePath));
                return new String(bytes, java.nio.charset.Charset.forName("GBK"));
            } catch (Exception gbkEx) {
                log.warn("Failed to read file content, fallback to filename inference: {}", filePath);
                return null;
            }
        }
    }

    @FunctionalInterface
    private interface StageValidator<T> {
        T validate(String rawJson);
    }

    @FunctionalInterface
    private interface StageFallback<T> {
        T fallback(RuntimeException ex);
    }

    private record StageResult<T>(T data, List<String> warnings, boolean inferred) {
    }

    /**
     * 提供商配置快照。
     */
    private record ProviderSettings(
            String key,
            String label,
            boolean enabled,
            String apiKey,
            String apiBase,
            String model,
            int timeout) {
    }
}
