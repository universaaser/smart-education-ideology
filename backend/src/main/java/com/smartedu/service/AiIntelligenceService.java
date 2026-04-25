package com.smartedu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.DocumentStructureDto;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.ResourceCitationDto;
import com.smartedu.dto.TeachingArtifactsDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.ParseTaskIdeologyMatch;
import com.smartedu.entity.ParseTaskKnowledgePoint;
import com.smartedu.entity.Resource;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.mapper.ParseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    public static final String TASK_CHAT = "chat";
    public static final String TASK_PARSE = "parse";
    public static final String TASK_IDEOLOGY = "ideology";
    public static final String TASK_QUESTION_GEN = "question-gen";
    public static final String TASK_CRAWL = "crawl";
    public static final String TASK_PATH = "path";

    private final ParseTaskMapper parseTaskMapper;
    private final ObjectMapper objectMapper;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final AiPipelineJsonValidator pipelineJsonValidator;
    private final CourseService courseService;
    private final ResourceService resourceService;
    private final DocumentTextExtractor documentTextExtractor;
    private final MineruParseClient mineruParseClient;
    private final AiStreamBuffer aiStreamBuffer;
    private final ParseTaskKnowledgePointMapper parseTaskKnowledgePointMapper;
    private final ParseTaskIdeologyMatchMapper parseTaskIdeologyMatchMapper;
    private final VectorIndexAsyncService vectorIndexAsyncService;

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
    @Value("${ai.routing.default-chat-provider:openai}")
    private String defaultChatProvider;

    @Value("${ai.routing.failure-threshold:3}")
    private int failureThreshold;

    // 熔断冷却期：达到阈值后等待该秒数再允许一次半开探活，避免熔断后永久跳过。
    @Value("${ai.routing.circuit-cool-down-seconds:60}")
    private long circuitCoolDownSeconds;

    // 后台任务回退链：逗号分隔的 provider key 列表，按顺序尝试，未 enabled 的节点会被自动跳过。
    @Value("${ai.routing.background-chain:openai}")
    private String backgroundChainConfig;

    @Value("${ai.routes.chat:${ai.routing.default-chat-provider:openai}}")
    private String chatRoute;

    @Value("${ai.routes.parse:${ai.routing.background-chain:openai}}")
    private String parseRoute;

    @Value("${ai.routes.ideology:${ai.routing.background-chain:openai}}")
    private String ideologyRoute;

    @Value("${ai.routes.question-gen:${ai.routing.background-chain:openai}}")
    private String questionGenerationRoute;

    @Value("${ai.routes.crawl:${ai.routing.background-chain:openai}}")
    private String crawlRoute;

    @Value("${ai.routes.path:${ai.routing.background-chain:openai}}")
    private String pathRoute;

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
     * 一旦达到失败阈值就加入该映射，value 为熔断起始时间戳（毫秒）。
     * 冷却期过后允许进入半开状态探活，成功则清除，失败则刷新时间戳继续熔断。
     */
    private final Map<String, Long> disabledProviders = new ConcurrentHashMap<>();

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

    public String chatForTask(String taskType, List<Map<String, String>> messages, String systemPrompt) {
        return callWithProviderChain(messages, systemPrompt, buildTaskProviderChain(taskType));
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
     * Gemini 预留入口。
     */
    private String callGemini(List<Map<String, String>> messages, String systemPrompt) {
        throw new RuntimeException("Gemini provider is not implemented");
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
        List<String> errors = new ArrayList<>();
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .build();

            if (shouldPreferResponsesApi(apiBase, model)) {
                try {
                    return callResponsesApi(client, apiBase, apiKey, model, messages, systemPrompt, providerLabel);
                } catch (RuntimeException ex) {
                    errors.add("responses: " + ex.getMessage());
                    log.warn("{} responses API failed, fallback to chat completions", providerLabel, ex);
                }
            }

            try {
                return callChatCompletionsApi(client, apiBase, apiKey, model, messages, systemPrompt, providerLabel);
            } catch (RuntimeException ex) {
                errors.add("chat.completions: " + ex.getMessage());
                throw new RuntimeException(String.join(" | ", errors), ex);
            }
        } catch (IOException e) {
            log.error("{} API call exception", providerLabel, e);
            throw new RuntimeException("AI service call failed: " + e.getMessage(), e);
        }
    }

    private String callChatCompletionsApi(
            OkHttpClient client,
            String apiBase,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            String systemPrompt,
            String providerLabel) throws IOException {
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
        if (shouldUseMaxCompletionTokens(model)) {
            requestBody.put("max_completion_tokens", DEFAULT_MAX_TOKENS);
        } else {
            requestBody.put("max_tokens", DEFAULT_MAX_TOKENS);
        }
        // 强制使用 SSE 流式：本地 Codex/ChatGPT 兼容代理在非流式下会返回 content=null，
        // 仅在 stream:true 时通过 delta.content 推送真实文本；其他官方/兼容服务也支持流式，
        // 因此统一走流式可以同时覆盖代理与官方接口。
        requestBody.put("stream", true);

        String endpoint = buildChatCompletionUrl(apiBase);
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() == null ? "" : response.body().string();
                log.error("{} chat completions failed: status={}, body={}", providerLabel, response.code(), errorBody);
                throw new RuntimeException(buildHttpErrorMessage(response.code(), errorBody));
            }

            String content = readChatCompletionStream(response.body());
            if (content.isBlank()) {
                throw new RuntimeException("empty assistant content from chat.completions");
            }
            return content;
        }
    }

    /**
     * 解析 OpenAI 兼容协议的 SSE 流，将 choices[0].delta.content 追加为完整文本。
     * 兼容 content 为字符串或 [{"type":"text","text":"..."}] 数组的两种格式。
     */
    private String readChatCompletionStream(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(body.charStream())) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    continue;
                }
                try {
                    JsonNode chunk = objectMapper.readTree(payload);
                    JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                    String piece = extractTextContent(delta);
                    if (!piece.isEmpty()) {
                        builder.append(piece);
                        // 把 LLM 流式增量推入 live-log 缓冲，供前端实时查看；
                        // 未绑定 taskId 的对话调用会被 appendChunk 内部静默忽略。
                        aiStreamBuffer.appendChunk(piece);
                    }
                } catch (IOException e) {
                    log.warn("Malformed SSE chunk skipped, payload={}", payload, e);
                }
            }
        }
        return builder.toString().trim();
    }

    private String callResponsesApi(
            OkHttpClient client,
            String apiBase,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            String systemPrompt,
            String providerLabel) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("input", messages);
        requestBody.put("instructions", systemPrompt);
        requestBody.put("max_output_tokens", DEFAULT_MAX_TOKENS);

        String endpoint = buildResponsesUrl(apiBase);
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
                log.error("{} responses API failed: status={}, body={}", providerLabel, response.code(), responseBody);
                throw new RuntimeException(buildHttpErrorMessage(response.code(), responseBody));
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            String content = extractResponsesContent(jsonNode);
            if (content.isBlank()) {
                throw new RuntimeException("empty assistant content from responses");
            }
            return content;
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

    private String buildResponsesUrl(String apiBase) {
        if (apiBase == null || apiBase.isBlank()) {
            throw new RuntimeException("AI service base-url is missing");
        }

        String normalized = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        if (normalized.endsWith("/responses")) {
            return normalized;
        }
        return normalized + "/responses";
    }

    /**
     * 后台任务默认链路：按 ai.routing.background-chain 配置顺序尝试，
     * 保持与 default-chat-provider 向后兼容——若 background-chain 未配置则回退到单节点默认值。
     */
    private List<String> buildBackgroundProviderChain() {
        List<String> chain = parseProviderChain(backgroundChainConfig);
        if (chain.isEmpty()) {
            chain = parseProviderChain(defaultChatProvider);
        }
        if (chain.isEmpty()) {
            chain = List.of("openai");
        }
        return chain;
    }

    /**
     * 对话场景链路：前端偏好节点优先，其后附加 background-chain 中剩余节点作为自动回退，
     * 前端无需感知后台切换逻辑。
     */
    private List<String> buildPreferredProviderChain(String preferredProvider) {
        return appendBackgroundFallback(parseProviderChain(preferredProvider));
    }

    private List<String> buildTaskProviderChain(String taskType) {
        return appendBackgroundFallback(parseProviderChain(resolveTaskRoute(taskType)));
    }

    private List<String> appendBackgroundFallback(List<String> primaryChain) {
        List<String> chain = new ArrayList<>(primaryChain == null ? List.of() : primaryChain);
        for (String key : buildBackgroundProviderChain()) {
            if (!chain.contains(key)) {
                chain.add(key);
            }
        }
        return chain;
    }

    private String resolveTaskRoute(String taskType) {
        String normalized = taskType == null ? "" : taskType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case TASK_CHAT -> chatRoute;
            case TASK_PARSE -> parseRoute;
            case TASK_IDEOLOGY -> ideologyRoute;
            case TASK_QUESTION_GEN -> questionGenerationRoute;
            case TASK_CRAWL -> crawlRoute;
            case TASK_PATH -> pathRoute;
            default -> backgroundChainConfig;
        };
    }

    /**
     * 解析逗号分隔的 provider 链配置，去重并过滤空串。
     */
    private List<String> parseProviderChain(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : raw.split(",")) {
            String normalized = normalizeProviderAlias(item);
            if (normalized.isBlank() || result.contains(normalized)) {
                continue;
            }
            result.add(normalized);
        }
        return result;
    }

    /**
     * 把历史别名（default）映射到实际的 provider key（proxy），未识别的 key 保留原值由
     * {@link #getProviderSettings} 再做最终校验。这里不再像旧实现那样把所有节点强制归一到 openai，
     * 以便真正启用多节点回退。
     */
    private String normalizeProviderAlias(String providerKey) {
        String normalized = normalizeProviderKey(providerKey);
        if ("default".equals(normalized)) {
            return "proxy";
        }
        return normalized;
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
            if (isCircuitOpen(providerSettings.key())) {
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
     * 连续失败达到阈值后直接熔断，同时记录熔断起始时间以便半开探活。
     */
    private void markProviderFailure(String providerKey, RuntimeException ex) {
        int safeThreshold = Math.max(failureThreshold, 1);
        int failures = providerFailureCounts.merge(providerKey, 1, Integer::sum);
        if (failures >= safeThreshold) {
            disabledProviders.put(providerKey, System.currentTimeMillis());
            log.error("AI provider disabled after consecutive failures: provider={}, failures={}", providerKey,
                    failures, ex);
            return;
        }

        log.warn("AI provider call failed: provider={}, failures={}", providerKey, failures, ex);
    }

    /**
     * 判断熔断是否仍然生效。冷却期过后自动移除标记，允许一次半开探活；
     * 若探活失败会在 {@link #markProviderFailure} 里重新打上时间戳继续熔断。
     */
    private boolean isCircuitOpen(String providerKey) {
        Long disabledSince = disabledProviders.get(providerKey);
        if (disabledSince == null) {
            return false;
        }
        long coolDownMillis = Math.max(circuitCoolDownSeconds, 0) * 1000L;
        if (coolDownMillis <= 0) {
            return true;
        }
        if (System.currentTimeMillis() - disabledSince >= coolDownMillis) {
            // 冷却期已过：清除熔断标记和失败计数，允许下一次请求真正尝试。
            disabledProviders.remove(providerKey);
            providerFailureCounts.remove(providerKey);
            log.info("AI provider circuit half-open after cool-down: provider={}", providerKey);
            return false;
        }
        return true;
    }

    private String normalizeProviderKey(String providerKey) {
        if (providerKey == null) {
            return "";
        }
        return providerKey.trim().toLowerCase();
    }

    private boolean shouldPreferResponsesApi(String model) {
        String normalizedModel = normalizeModelName(model);
        return normalizedModel.startsWith("gpt-5")
                || normalizedModel.startsWith("o1")
                || normalizedModel.startsWith("o3")
                || normalizedModel.startsWith("o4");
    }

    /**
     * The localhost OpenAI-compatible proxy exposes GPT-5 text only via streaming
     * chat.completions, so /responses must be skipped there to avoid a guaranteed
     * empty call.
     */
    private boolean shouldPreferResponsesApi(String apiBase, String model) {
        return shouldPreferResponsesApi(model) && !isLocalCompatibleBaseUrl(apiBase);
    }

    private boolean shouldUseMaxCompletionTokens(String model) {
        return shouldPreferResponsesApi(model);
    }

    private String normalizeModelName(String model) {
        if (model == null) {
            return "";
        }
        return model.trim().toLowerCase();
    }

    private boolean isLocalCompatibleBaseUrl(String apiBase) {
        if (apiBase == null || apiBase.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(apiBase.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String normalizedHost = host.trim().toLowerCase();
            return "localhost".equals(normalizedHost)
                    || "127.0.0.1".equals(normalizedHost)
                    || "::1".equals(normalizedHost);
        } catch (IllegalArgumentException ex) {
            String normalized = apiBase.trim().toLowerCase();
            return normalized.contains("://localhost")
                    || normalized.contains("://127.0.0.1")
                    || normalized.contains("[::1]");
        }
    }

    private String buildHttpErrorMessage(int statusCode, String responseBody) {
        String serviceMessage = extractServiceErrorMessage(responseBody);
        if (serviceMessage.isBlank()) {
            return "AI service call failed: " + statusCode;
        }
        return "AI service call failed: " + statusCode + " - " + serviceMessage;
    }

    private String extractServiceErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.path("error").isTextual()) {
                return node.path("error").asText();
            }
            if (node.path("error").path("message").isTextual()) {
                return node.path("error").path("message").asText();
            }
            if (node.path("message").isTextual()) {
                return node.path("message").asText();
            }
        } catch (Exception ignored) {
            return responseBody;
        }
        return responseBody;
    }

    private String extractResponsesContent(JsonNode jsonNode) {
        if (jsonNode == null) {
            return "";
        }

        String directOutput = extractTextContent(jsonNode.path("output_text"));
        if (!directOutput.isBlank()) {
            return directOutput;
        }

        JsonNode outputNode = jsonNode.path("output");
        if (!outputNode.isArray()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode outputItem : outputNode) {
            String itemText = extractTextContent(outputItem.path("text"));
            if (!itemText.isBlank()) {
                appendWithNewline(builder, itemText);
            }

            JsonNode contentNode = outputItem.path("content");
            if (!contentNode.isArray()) {
                continue;
            }
            for (JsonNode contentItem : contentNode) {
                String text = extractTextContent(contentItem.path("text"));
                if (!text.isBlank()) {
                    appendWithNewline(builder, text);
                }
                String outputText = extractTextContent(contentItem.path("output_text"));
                if (!outputText.isBlank()) {
                    appendWithNewline(builder, outputText);
                }
            }
        }
        return builder.toString().trim();
    }

    private String extractTextContent(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (!node.isArray()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                appendWithNewline(builder, item.asText());
                continue;
            }
            if ("text".equals(item.path("type").asText())) {
                appendWithNewline(builder, item.path("text").asText());
                continue;
            }
            if (item.path("text").isTextual()) {
                appendWithNewline(builder, item.path("text").asText());
            }
        }
        return builder.toString().trim();
    }

    private void appendWithNewline(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value.trim());
    }

    /**
     * 去重时保留顺序，确保回退链路可控。
     */
    /**
     * 提取专业内容中的思政价值。
     */
    public String extractIdeologicalValue(String technicalContent, String subject) {
        String systemPrompt = "You are an expert in curriculum ideology integration. "
                + "Analyze the given subject content and provide teaching suggestions from the perspectives of craftsmanship, national responsibility, professional ethics, and critical thinking.";

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "Analyze the ideological value in the following subject content for \"" + subject
                + "\" and provide classroom integration suggestions:\n\n" + technicalContent);
        messages.add(userMsg);

        return chatForTask(TASK_IDEOLOGY, messages, systemPrompt);
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

        // 绑定实时日志上下文：后续 executeStage / readChatCompletionStream 会向该缓冲写入内容。
        aiStreamBuffer.beginTask(taskId);
        try {
            runPipeline(task, true, 1);
            log.info("Document parse completed: taskId={}", taskId);
        } catch (Exception e) {
            log.error("Document parse failed: taskId={}", taskId, e);
            // 终态写入必须独立于 runPipeline 的内存态 task：
            // 之前 runPipeline 可能已经把巨型 pipelineResult JSON 塞进 task.aiAnalysis；
            // 如果抛出的异常正是由该字段过大/写库失败触发，
            // 用同一个 task 再次 updateById 会继续失败，任务会永远卡在 ANALYZING。
            // 这里从 DB 重载一份纯净 task，仅翻 status/currentStep/errorMessage 落库。
            try {
                ParseTask finalTask = parseTaskMapper.selectById(taskId);
                if (finalTask != null) {
                    recordTaskFailure(finalTask, e, List.of(), "Pipeline failed");
                }
            } catch (Exception persistEx) {
                log.error("Failed to persist FAILED status for taskId={}", taskId, persistEx);
            }
        } finally {
            aiStreamBuffer.endTask();
        }
    }

    /**
     * 基于已有解析结果重新生成结构化教学内容。
     */
    public ParseTask reparseTask(Long taskId) {
        return restartTask(taskId, "COMPLETED", "Reparse requested");
    }

    public ParseTask retryTask(Long taskId) {
        return restartTask(taskId, "FAILED", "Retry requested");
    }

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
            deleteProjectionsByTaskId(taskId);
            return runPipeline(task, false, Math.max(1, regenerateRetry + 1));
        } catch (RuntimeException ex) {
            recordTaskFailure(task, ex, List.of(), "Regenerate failed");
            throw ex;
        }
    }

    private ParseTask restartTask(Long taskId, String requiredStatus, String currentStep) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("Parse task not found");
        }
        if (!requiredStatus.equalsIgnoreCase(safe(task.getStatus()))) {
            throw new RuntimeException("Task status does not support this action: " + safe(task.getStatus()));
        }

        deleteProjectionsByTaskId(taskId);
        vectorIndexAsyncService.deletePipelineResult(taskId);
        aiStreamBuffer.discard(taskId);

        task.setStatus("UPLOADING");
        task.setProgress(10);
        task.setCurrentStep(currentStep);
        task.setParsedContent(null);
        task.setAiAnalysis(null);
        task.setErrorMessage(null);
        task.setStartedAt(LocalDateTime.now());
        task.setCompletedAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        parseTaskMapper.updateById(task);

        return task;
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
        String fileName = extractFileName(task.getFilePath());
        String courseContext = buildCourseContext(task.getCourseId());
        List<String> warnings = new ArrayList<>();
        boolean inferred = false;

        // 以 MinerU 为文档解析首选，失败/未配置时回退到本地 POI/PDFBox 抽取。
        // rawContent 后续被所有 LLM 阶段当作原文输入；parseMode/rawMarkdown 存入 DocumentStructureDto
        // 便于追溯。
        String rawContent;
        String rawMarkdown = null;
        String parseMode;
        // MinerU 结构化产物（大纲 / 表格 / 图片 / 公式 / 统计），LLM 阶段用于生成更贴合章节的结果。
        com.smartedu.dto.mineru.MineruStructuredContentDto mineruStructured = null;
        if (includeDocumentParse) {
            updateTaskProgress(task, "PARSING", 15, "Parsing document with MinerU...");
            MineruParseClient.MineruParseResult mineru = tryMineruParse(task, fileName, warnings);
            if (mineru != null && mineru.markdown != null && !mineru.markdown.isBlank()) {
                rawContent = mineru.markdown;
                rawMarkdown = mineru.markdown;
                parseMode = "MINERU";
                mineruStructured = mineru.structuredContent;
                aiStreamBuffer.appendStage("mineru-parse ok batchId=" + mineru.batchId
                        + (mineruStructured != null && mineruStructured.getStats() != null
                                ? " pages=" + mineruStructured.getStats().getPageCount()
                                : ""));
                task.setParsedContent(writeParsedContentJson(fileName, rawMarkdown, parseMode));
                task.setUpdatedAt(LocalDateTime.now());
                parseTaskMapper.updateById(task);
            } else {
                rawContent = readFileContent(task.getFilePath());
                parseMode = "FALLBACK_LLM";
                aiStreamBuffer.appendStage("mineru-parse unavailable, fallback to text extractor");
                if (rawContent != null && !rawContent.isBlank()) {
                    rawMarkdown = rawContent;
                    task.setParsedContent(writeParsedContentJson(fileName, rawMarkdown, parseMode));
                    task.setUpdatedAt(LocalDateTime.now());
                    parseTaskMapper.updateById(task);
                }
            }
        } else {
            parseMode = "REGENERATE";
            rawMarkdown = extractExistingRawMarkdown(task);
            rawContent = rawMarkdown;
            warnings.add("Document structure rebuilt from existing parsed content for regenerate.");
        }

        DocumentStructureDto structure;
        if (includeDocumentParse) {
            updateTaskProgress(task, "PARSING", 25, "Extracting document structure...");
            StageResult<DocumentStructureDto> parseResult = parseDocumentStructure(rawContent, fileName, courseContext);
            structure = parseResult.data();
            warnings.addAll(parseResult.warnings());
            inferred = inferred || parseResult.inferred();
            if (rawMarkdown != null) {
                structure.setRawMarkdown(rawMarkdown);
            }
            structure.setParseMode(parseMode);
            if (mineruStructured != null) {
                structure.setMineruContent(mineruStructured);
                // 用 MinerU 大纲回填 structure.chapterOutline / teachingFocus，保证 LLM 幻觉导致的空结果也能兜底。
                if (structure.getChapterOutline() == null || structure.getChapterOutline().isEmpty()) {
                    structure.setChapterOutline(collectOutlineTitles(mineruStructured));
                }
            }
            task.setUpdatedAt(LocalDateTime.now());
            parseTaskMapper.updateById(task);
        } else {
            DocumentStructureDto existing = loadExistingStructure(task);
            if (existing != null) {
                structure = existing;
            } else {
                // 老任务可能只有 parsedContent 是结构化 JSON，保留一次兼容读取。
                try {
                    structure = objectMapper.readValue(task.getParsedContent(), DocumentStructureDto.class);
                } catch (Exception ex) {
                    throw new RuntimeException("Existing parsed content is invalid: unable to reuse structure", ex);
                }
            }
            if (rawMarkdown == null && structure.getRawMarkdown() != null) {
                rawMarkdown = structure.getRawMarkdown();
            }
            if (structure.getParseMode() == null || structure.getParseMode().isBlank()) {
                structure.setParseMode(parseMode);
            }
            // 沿用原结构化产物，避免 regenerate 阶段把已有 MinerU 数据丢失。
            if (mineruStructured == null && structure.getMineruContent() != null) {
                mineruStructured = structure.getMineruContent();
            }
        }

        updateTaskProgress(task, "PARSING", 40, "Extracting knowledge points...");
        StageResult<List<KnowledgePointDto>> knowledgeResult = extractKnowledgePoints(structure,
                truncateInput(rawContent), fileName, courseContext);
        List<KnowledgePointDto> knowledgePoints = knowledgeResult.data();
        warnings.addAll(knowledgeResult.warnings());
        inferred = inferred || knowledgeResult.inferred();

        updateTaskProgress(task, "ANALYZING", 60, "Matching ideology elements...");
        StageResult<List<IdeologyMatchDto>> ideologyResult = matchIdeologyElements(structure, knowledgePoints,
                courseContext);
        List<IdeologyMatchDto> ideologyMatches = ideologyResult.data();
        warnings.addAll(ideologyResult.warnings());
        inferred = inferred || ideologyResult.inferred();

        updateTaskProgress(task, "ANALYZING", 80, "Generating teaching artifacts...");
        StageResult<TeachingArtifactsDto> artifactResult = generateTeachingArtifacts(structure, knowledgePoints,
                ideologyMatches, courseContext);
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

        task.setParsedContent(writeJsonSafely(structure));
        task.setAiAnalysis(writeJsonSafely(pipelineResult));
        task.setUpdatedAt(LocalDateTime.now());
        parseTaskMapper.updateById(task);
        persistPipelineProjections(task, pipelineResult);
        vectorIndexAsyncService.indexPipelineResult(task, pipelineResult);

        // Uploaded document parse results stay in parse task, projection and vector index storage only.
        if (!inferred) {
            updateTaskProgress(task, "ANALYZING", 90, "Finalizing parsed outputs...");

            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCurrentStep("Completed");
            task.setCompletedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            parseTaskMapper.updateById(task);
        } else {
            // 所有阶段都回退到 stub：任务视为失败，不入库，errorMessage 汇总 warnings 供前端/运维排查。
            recordTaskFailure(task, null, warnings, "AI pipeline fell back to stub; knowledge graph not updated.");
        }

        // regenerate-retry 用于重新生成场景；首次流程固定为 1 次重试。
        if (pipelineAttempts > 1 && inferred) {
            for (int i = 1; i < pipelineAttempts; i++) {
                PipelineResultDto retryResult = tryRebuildFromParsedContent(task, structure, rawContent, courseContext);
                if (!retryResult.isInferred()) {
                    updateTaskProgress(task, "ANALYZING", 90, "Finalizing parsed outputs...");
                    task.setStatus("COMPLETED");
                    task.setProgress(100);
                    task.setCurrentStep("Completed");
                    task.setCompletedAt(LocalDateTime.now());
                    task.setErrorMessage(null);
                    parseTaskMapper.updateById(task);
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
        StageResult<List<KnowledgePointDto>> knowledgeResult = extractKnowledgePoints(structure,
                truncateInput(rawContent), extractFileName(task.getFilePath()), courseContext);
        StageResult<List<IdeologyMatchDto>> ideologyResult = matchIdeologyElements(structure, knowledgeResult.data(),
                courseContext);
        StageResult<TeachingArtifactsDto> artifactResult = generateTeachingArtifacts(structure, knowledgeResult.data(),
                ideologyResult.data(), courseContext);
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

        task.setParsedContent(writeJsonSafely(structure));
        task.setAiAnalysis(writeJsonSafely(pipelineResult));
        task.setUpdatedAt(LocalDateTime.now());
        parseTaskMapper.updateById(task);
        persistPipelineProjections(task, pipelineResult);
        vectorIndexAsyncService.indexPipelineResult(task, pipelineResult);
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
                TASK_PARSE,
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
                Each item keys: pointName, definition, chapter, importance, evidenceSnippet, resourceCitations.
                importance must be HIGH or MEDIUM or LOW.
                resourceCitations is an array with max 3 items.
                Each citation keys: resourceRefId, resourceId, title, source, sourceUrl, quotedExcerpt, citationReason.
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

                Keep evidenceSnippet grounded in the uploaded document.
                If related resource excerpts are present in Course context, add up to 3 resourceCitations per knowledge point.
                resourceRefId must reuse the ids shown in Course context such as R1 or R2.
                quotedExcerpt must quote or paraphrase the provided resource excerpt only, not invent new source text.
                citationReason must explain why that resource helps interpret the knowledge point.
                If no related resource applies, return an empty resourceCitations array.
                """
                .formatted(
                        maxKnowledgePoints,
                        fileName,
                        safeCourseContext(courseContext),
                        writeStructureForPrompt(structure),
                        rawContent);

        return executeStage(
                "knowledge-point-extraction",
                systemPrompt,
                userPrompt,
                TASK_PARSE,
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
                Each item keys: knowledgePointName, ideologyElement, matchReason, confidence, citationExplanation, resourceCitations.
                confidence must be integer between 0 and 100.
                ideologyElement must be one short phrase.
                citationExplanation must explain the ideology match with support from resource excerpts when available.
                resourceCitations is an array with max 3 items.
                Each citation keys: resourceRefId, resourceId, title, source, sourceUrl, quotedExcerpt, citationReason.
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

                When related resource excerpts are present in Course context, use them to support ideology matching.
                citationExplanation should explain why the ideology element is justified by the knowledge point together with the cited resource excerpts.
                resourceRefId must reuse the ids shown in Course context such as R1 or R2.
                quotedExcerpt must stay within the provided resource excerpt content.
                If no related resource applies, return an empty resourceCitations array and an empty citationExplanation string.
                """
                .formatted(
                        maxIdeologyMatchesPerPoint,
                        safeCourseContext(courseContext),
                        writeStructureForPrompt(structure),
                        writeJsonSafely(knowledgePoints));

        return executeStage(
                "ideology-matching",
                systemPrompt,
                userPrompt,
                TASK_IDEOLOGY,
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
                Each question keys: questionType, difficulty, knowledgePointId, stem, options, referenceAnswer, scoringPoints.
                questionType must be SINGLE_CHOICE, MULTIPLE_CHOICE, SHORT_ANSWER, or CASE_ANALYSIS.
                difficulty must be EASY, MEDIUM, or HARD.
                knowledgePointId may be null when no reliable id exists.
                options and scoringPoints are arrays of short strings.
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
                writeStructureForPrompt(structure),
                writeJsonSafely(knowledgePoints),
                writeJsonSafely(ideologyMatches));

        return executeStage(
                "teaching-artifact-generation",
                systemPrompt,
                userPrompt,
                TASK_QUESTION_GEN,
                this::validateTeachingArtifacts,
                this::buildTeachingArtifactsFallback);
    }

    private <T> StageResult<T> executeStage(
            String stageName,
            String systemPrompt,
            String userPrompt,
            String taskType,
            StageValidator<T> validator,
            StageFallback<T> fallbackSupplier) {
        List<String> warnings = new ArrayList<>();
        RuntimeException lastException = null;
        int attempts = 2;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            aiStreamBuffer.appendStage(stageName + " attempt " + attempt);
            try {
                String result = callStructuredPrompt(taskType, systemPrompt, userPrompt);
                T validated = validator.validate(result);
                return new StageResult<>(validated, warnings, false);
            } catch (RuntimeException ex) {
                lastException = ex;
                warnings.add(stageName + " attempt " + attempt + " failed: " + ex.getMessage());
            }
        }

        aiStreamBuffer.appendStage(stageName + " fallback used (stub)");
        T fallback = fallbackSupplier.fallback(lastException);
        warnings.add(stageName + " fallback used.");
        return new StageResult<>(fallback, warnings, true);
    }

    private String callStructuredPrompt(String taskType, String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        return chatForTask(taskType, messages, systemPrompt);
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
                    List.of("pointName", "definition", "chapter", "importance", "evidenceSnippet", "resourceCitations"),
                    List.of("pointName", "definition", "chapter", "importance", "evidenceSnippet",
                            "resourceCitations"));

            String importance = safeText(pointNode.get("importance"));
            if (!List.of("HIGH", "MEDIUM", "LOW").contains(importance)) {
                throw new IllegalArgumentException("Invalid importance: " + importance);
            }

            KnowledgePointDto dto = objectMapper.convertValue(pointNode, KnowledgePointDto.class);
            dto.setPointName(trimToLength(dto.getPointName(), 120));
            dto.setDefinition(trimToLength(dto.getDefinition(), 500));
            dto.setChapter(trimToLength(dto.getChapter(), 120));
            dto.setEvidenceSnippet(trimToLength(dto.getEvidenceSnippet(), 300));
            dto.setResourceCitations(sanitizeResourceCitations(pointNode.get("resourceCitations")));
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
        pipelineJsonValidator.validateArraySize(node, "ideologyMatches",
                Math.max(1, maxKnowledgePoints * maxIdeologyMatchesPerPoint));

        List<IdeologyMatchDto> result = new ArrayList<>();
        for (JsonNode matchNode : matchesNode) {
            pipelineJsonValidator.parseObject(
                    matchNode.toString(),
                    List.of("knowledgePointName", "ideologyElement", "matchReason", "confidence", "citationExplanation",
                            "resourceCitations"),
                    List.of("knowledgePointName", "ideologyElement", "matchReason", "confidence", "citationExplanation",
                            "resourceCitations"));

            int confidence = matchNode.get("confidence").asInt(-1);
            if (confidence < 0 || confidence > 100) {
                throw new IllegalArgumentException("confidence must be in [0, 100]");
            }

            IdeologyMatchDto dto = objectMapper.convertValue(matchNode, IdeologyMatchDto.class);
            dto.setKnowledgePointName(trimToLength(dto.getKnowledgePointName(), 120));
            dto.setIdeologyElement(trimToLength(dto.getIdeologyElement(), 120));
            dto.setMatchReason(trimToLength(dto.getMatchReason(), 500));
            dto.setCitationExplanation(trimToLength(dto.getCitationExplanation(), 600));
            dto.setResourceCitations(sanitizeResourceCitations(matchNode.get("resourceCitations")));
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
                    List.of("questionType", "difficulty", "knowledgePointId", "stem", "options", "referenceAnswer",
                            "scoringPoints"));
            if (questionNode.get("options") != null && !questionNode.get("options").isArray()) {
                throw new IllegalArgumentException("options must be an array");
            }
            if (questionNode.get("scoringPoints") == null || !questionNode.get("scoringPoints").isArray()) {
                throw new IllegalArgumentException("scoringPoints must be an array");
            }
        }

        TeachingArtifactsDto dto = objectMapper.convertValue(node, TeachingArtifactsDto.class);
        dto.setQuestions(sanitizeQuestionList(dto.getQuestions()));
        dto.setLectureNotes(trimToLength(dto.getLectureNotes(), 5000));
        if (dto.getCases() == null) {
            dto.setCases(new ArrayList<>());
        }
        if (dto.getQuestions() == null) {
            dto.setQuestions(new ArrayList<>());
        }
        return dto;
    }

    private List<TeachingArtifactsDto.QuestionDto> sanitizeQuestionList(List<TeachingArtifactsDto.QuestionDto> source) {
        List<TeachingArtifactsDto.QuestionDto> questions = new ArrayList<>();
        if (source == null) {
            return questions;
        }
        for (TeachingArtifactsDto.QuestionDto question : source) {
            if (question == null) {
                continue;
            }
            TeachingArtifactsDto.QuestionDto sanitized = new TeachingArtifactsDto.QuestionDto();
            sanitized.setQuestionType(normalizeQuestionType(question.getQuestionType()));
            sanitized.setDifficulty(normalizeDifficulty(question.getDifficulty()));
            sanitized.setKnowledgePointId(question.getKnowledgePointId());
            sanitized.setStem(trimToLength(question.getStem(), 2000));
            sanitized.setOptions(trimStringList(question.getOptions(), 1000));
            sanitized.setReferenceAnswer(trimToLength(question.getReferenceAnswer(), 3000));
            sanitized.setScoringPoints(trimStringList(question.getScoringPoints(), 2000));
            questions.add(sanitized);
        }
        return questions;
    }

    private String normalizeQuestionType(String value) {
        String normalized = safe(value).toUpperCase(Locale.ROOT);
        if (List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "SHORT_ANSWER", "CASE_ANALYSIS").contains(normalized)) {
            return normalized;
        }
        return "SHORT_ANSWER";
    }

    private String normalizeDifficulty(String value) {
        String normalized = safe(value).toUpperCase(Locale.ROOT);
        if (List.of("EASY", "MEDIUM", "HARD").contains(normalized)) {
            return normalized;
        }
        return "MEDIUM";
    }

    private List<String> trimStringList(List<String> source, int maxLength) {
        if (source == null) {
            return new ArrayList<>();
        }
        return source.stream()
                .map(item -> trimToLength(item, maxLength))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
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
        KnowledgePointDto point = new KnowledgePointDto();
        point.setPointName("Pending Knowledge Point");
        point.setDefinition("Knowledge extraction fallback result.");
        point.setChapter("Unknown Chapter");
        point.setImportance("MEDIUM");
        point.setEvidenceSnippet("No reliable evidence snippet from provider.");
        point.setResourceCitations(new ArrayList<>());
        fallback.add(point);
        return fallback;
    }

    private List<IdeologyMatchDto> buildIdeologyMatchFallback(List<KnowledgePointDto> points) {
        List<IdeologyMatchDto> fallback = new ArrayList<>();
        for (KnowledgePointDto point : points) {
            IdeologyMatchDto match = new IdeologyMatchDto();
            match.setKnowledgePointName(point.getPointName());
            match.setIdeologyElement("Craftsmanship Spirit");
            match.setMatchReason("Fallback ideology mapping due to provider instability.");
            match.setConfidence(55);
            match.setCitationExplanation("");
            match.setResourceCitations(new ArrayList<>());
            fallback.add(match);
        }
        return fallback;
    }

    private TeachingArtifactsDto buildTeachingArtifactsFallback(RuntimeException ex) {
        TeachingArtifactsDto fallback = new TeachingArtifactsDto();
        fallback.setLectureNotes("Teaching artifact generation fallback result.");
        fallback.setCases(List.of("Case generation is pending due to provider fallback."));
        fallback.setQuestions(List.of(
                new TeachingArtifactsDto.QuestionDto(
                        "SHORT_ANSWER",
                        "MEDIUM",
                        null,
                        "Explain the key concept and its ideology relevance.",
                        new ArrayList<>(),
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
                // 兼容两种写法：旧任务是 DocumentStructureDto JSON；新任务是 MinerU markdown 原文。
                try {
                    emptyResult.setDocumentStructure(objectMapper.readValue(parsedContent, DocumentStructureDto.class));
                } catch (Exception ignore) {
                    DocumentStructureDto ds = new DocumentStructureDto();
                    ds.setRawMarkdown(parsedContent);
                    emptyResult.setDocumentStructure(ds);
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
                // 老任务 parsedContent 可能是结构化 JSON；MinerU 接入后通常是 markdown。
                // 非结构化时忽略即可，前端仍能从 aiAnalysis 其他字段取数据。
                try {
                    result.setDocumentStructure(objectMapper.readValue(parsedContent, DocumentStructureDto.class));
                } catch (Exception ignore) {
                    // parsedContent is not a JSON structure (likely markdown); skip silently.
                }
            }
            return result;
        } catch (Exception ex) {
            throw new RuntimeException("Pipeline result JSON is invalid", ex);
        }
    }

    /**
     * 面向 LLM 的结构摘要：剔除 rawMarkdown 与巨量 mineruContent.blocks，保留标题、类型、概要、
     * 章节大纲以及 MinerU 的统计 / 大纲 / 表格-图片-公式的摘要信息。
     *
     * <p>
     * 目的是既让 LLM 看到章节-页码-表格-图片结构，又不把完整 markdown / bbox / 全量 block 塞进提示词。
     */
    private String writeStructureForPrompt(DocumentStructureDto structure) {
        if (structure == null) {
            return "{}";
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("title", structure.getTitle());
        summary.put("documentType", structure.getDocumentType());
        summary.put("overview", structure.getOverview());
        summary.put("chapterOutline", structure.getChapterOutline());
        summary.put("teachingFocus", structure.getTeachingFocus());
        summary.put("parseMode", structure.getParseMode());

        com.smartedu.dto.mineru.MineruStructuredContentDto mc = structure.getMineruContent();
        if (mc != null) {
            Map<String, Object> mineru = new HashMap<>();
            if (mc.getStats() != null) {
                mineru.put("stats", mc.getStats());
            }
            if (mc.getOutline() != null && !mc.getOutline().isEmpty()) {
                mineru.put("outline", mc.getOutline());
            }
            // 表格/图片/公式各取前 8 条的 caption + 位置，给 LLM 足够上下文又不爆长度。
            if (mc.getTables() != null && !mc.getTables().isEmpty()) {
                mineru.put("tableSamples", summarizeBlocks(mc.getTables(), 8, true));
            }
            if (mc.getImages() != null && !mc.getImages().isEmpty()) {
                mineru.put("imageSamples", summarizeBlocks(mc.getImages(), 8, false));
            }
            if (mc.getEquations() != null && !mc.getEquations().isEmpty()) {
                mineru.put("equationSamples", summarizeBlocks(mc.getEquations(), 6, false));
            }
            summary.put("mineru", mineru);
        }
        return writeJsonSafely(summary);
    }

    /**
     * 把 block 列表缩成 LLM 友好的轻量概要：类型、页码、caption 或 latex 文本。
     */
    private List<Map<String, Object>> summarizeBlocks(
            List<com.smartedu.dto.mineru.MineruContentBlockDto> blocks,
            int maxItems,
            boolean includeTableSnippet) {
        List<Map<String, Object>> list = new ArrayList<>();
        int limit = Math.min(maxItems, blocks.size());
        for (int i = 0; i < limit; i++) {
            com.smartedu.dto.mineru.MineruContentBlockDto b = blocks.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("index", b.getIndex());
            item.put("type", b.getType());
            if (b.getPageIdx() != null)
                item.put("pageIdx", b.getPageIdx());
            if (b.getTableCaption() != null)
                item.put("caption", String.join(" ", b.getTableCaption()));
            else if (b.getImageCaption() != null)
                item.put("caption", String.join(" ", b.getImageCaption()));
            if (b.getText() != null && !b.getText().isBlank()) {
                item.put("text", trimToLength(b.getText(), 200));
            }
            if (includeTableSnippet && b.getTableBody() != null) {
                item.put("tableSnippet", trimToLength(b.getTableBody(), 400));
            }
            list.add(item);
        }
        return list;
    }

    /**
     * 由 MinerU 大纲树收集一条扁平的章节字符串序列，用于回填 DocumentStructureDto.chapterOutline。
     * 仅取一级 / 二级标题，避免列表过长。
     */
    private List<String> collectOutlineTitles(com.smartedu.dto.mineru.MineruStructuredContentDto mc) {
        List<String> titles = new ArrayList<>();
        if (mc == null || mc.getOutline() == null)
            return titles;
        for (com.smartedu.dto.mineru.MineruOutlineNodeDto n : mc.getOutline()) {
            if (n.getTitle() != null && !n.getTitle().isBlank()) {
                titles.add(n.getTitle().trim());
            }
            if (n.getChildren() != null) {
                for (com.smartedu.dto.mineru.MineruOutlineNodeDto c : n.getChildren()) {
                    if (c.getTitle() != null && !c.getTitle().isBlank()) {
                        titles.add("  " + c.getTitle().trim());
                    }
                }
            }
            if (titles.size() >= 30)
                break;
        }
        return titles;
    }

    private String writeParsedContentJson(String fileName, String rawMarkdown, String parseMode) {
        DocumentStructureDto parsedContent = new DocumentStructureDto();
        parsedContent.setTitle(fileName);
        parsedContent.setDocumentType("UNKNOWN");
        parsedContent.setOverview(trimToLength(rawMarkdown, 1200));
        parsedContent.setRawMarkdown(rawMarkdown);
        parsedContent.setParseMode(parseMode);
        return writeJsonSafely(parsedContent);
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
            StringBuilder context = new StringBuilder();
            context.append("courseId=").append(courseId).append('\n');

            List<KnowledgeNodeView> nodes = courseService == null
                    ? new ArrayList<>()
                    : courseService.getCourseKnowledgePoints(courseId);
            if (nodes == null) {
                nodes = new ArrayList<>();
            }

            if (nodes.isEmpty()) {
                context.append("No course knowledge points found.\n");
                return context.toString();
            }

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
            appendResourceContext(context, nodes);
            return context.toString();
        } catch (Exception ex) {
            log.warn("Failed to build course context, fallback to empty context: courseId={}", courseId, ex);
            return "";
        }
    }

    private void appendResourceContext(StringBuilder context, List<KnowledgeNodeView> nodes) {
        if (resourceService == null || nodes == null || nodes.isEmpty()) {
            return;
        }
        String keyword = buildResourceSearchKeyword(nodes);
        if (keyword.isBlank()) {
            return;
        }
        List<Resource> resources = resourceService.searchForChatContext(keyword, 3);
        if (resources == null || resources.isEmpty()) {
            return;
        }

        context.append("Related resource excerpts:\n");
        int index = 1;
        for (Resource resource : resources) {
            if (resource == null) {
                continue;
            }
            String refId = "R" + index;
            context.append("- [").append(refId).append("] id=")
                    .append(resource.getId() == null ? "" : resource.getId())
                    .append(" | title: ")
                    .append(trimToLength(resource.getTitle(), 160))
                    .append(" | source: ")
                    .append(trimToLength(resource.getSource(), 120))
                    .append(" | url: ")
                    .append(trimToLength(resource.getSourceUrl(), 240))
                    .append('\n');
            context.append("  excerpt: ")
                    .append(trimToLength(firstNonBlank(resource.getContent(), resource.getIdeologySummary()), 220))
                    .append('\n');
            context.append("  ideologySummary: ")
                    .append(trimToLength(resource.getIdeologySummary(), 180))
                    .append('\n');
            index++;
        }
    }

    private String buildResourceSearchKeyword(List<KnowledgeNodeView> nodes) {
        StringBuilder keyword = new StringBuilder();
        int count = 0;
        for (KnowledgeNodeView node : nodes) {
            if (node == null) {
                continue;
            }
            String name = safe(node.getName());
            if (name.isBlank()) {
                continue;
            }
            if (keyword.length() > 0) {
                keyword.append(' ');
            }
            keyword.append(name);
            count++;
            if (count >= 5) {
                break;
            }
        }
        return keyword.toString();
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

    /**
     * Phase 0: unified failure recorder. Keeps a short summary in the DB (bounded
     * to fit {@code parse_tasks.error_message}) and leaves full stack / warnings
     * to the application log so the column is never at risk of truncation.
     */
    private void recordTaskFailure(ParseTask task, Throwable cause, List<String> warnings, String step) {
        if (task == null) {
            return;
        }
        String summary;
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            summary = cause.getMessage();
        } else if (warnings != null && !warnings.isEmpty()) {
            summary = String.join(" | ", warnings);
        } else {
            summary = "Unknown pipeline failure";
        }
        task.setStatus("FAILED");
        task.setErrorMessage(trimToLength(summary, 480));
        if (step != null && !step.isBlank()) {
            task.setCurrentStep(trimToLength(step, 200));
        }
        task.setUpdatedAt(LocalDateTime.now());
        try {
            parseTaskMapper.updateById(task);
        } catch (Exception persistEx) {
            log.error("Failed to persist FAILED status for taskId={}", task.getId(), persistEx);
        }
        log.error("Parse task failed taskId={} step={} warnings={}", task.getId(), step, warnings, cause);
    }

    /**
     * Phase 2: delete existing projection rows for a parse task before
     * re-insertion.
     */
    private void deleteProjectionsByTaskId(Long taskId) {
        if (taskId == null) {
            return;
        }
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParseTaskKnowledgePoint> kpWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            kpWrapper.eq(ParseTaskKnowledgePoint::getParseTaskId, taskId);
            parseTaskKnowledgePointMapper.delete(kpWrapper);

            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParseTaskIdeologyMatch> imWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            imWrapper.eq(ParseTaskIdeologyMatch::getParseTaskId, taskId);
            parseTaskIdeologyMatchMapper.delete(imWrapper);
        } catch (Exception ex) {
            log.warn("Failed to delete old projections for taskId={}", taskId, ex);
        }
    }

    /**
     * Phase 2: persist pipeline result knowledge points and ideology matches into
     * projection tables.
     * Errors are logged but do not fail the main pipeline.
     */
    private void persistPipelineProjections(ParseTask task, PipelineResultDto result) {
        if (task == null || task.getId() == null || result == null) {
            return;
        }
        Long taskId = task.getId();
        Long courseId = task.getCourseId();
        String version = schemaVersion;
        try {
            deleteProjectionsByTaskId(taskId);

            if (result.getKnowledgePoints() != null) {
                for (KnowledgePointDto dto : result.getKnowledgePoints()) {
                    ParseTaskKnowledgePoint point = new ParseTaskKnowledgePoint();
                    point.setParseTaskId(taskId);
                    point.setCourseId(courseId);
                    point.setPointName(dto.getPointName());
                    point.setDefinition(dto.getDefinition());
                    point.setChapter(dto.getChapter());
                    point.setEvidenceSnippet(dto.getEvidenceSnippet());
                    point.setResourceCitationsJson(writeJsonSafely(dto.getResourceCitations()));
                    point.setPipelineVersion(version);
                    parseTaskKnowledgePointMapper.insert(point);
                }
            }

            if (result.getIdeologyMatches() != null) {
                for (IdeologyMatchDto dto : result.getIdeologyMatches()) {
                    ParseTaskIdeologyMatch match = new ParseTaskIdeologyMatch();
                    match.setParseTaskId(taskId);
                    match.setKnowledgePointName(dto.getKnowledgePointName());
                    match.setIdeologyElement(dto.getIdeologyElement());
                    match.setMatchReason(dto.getMatchReason());
                    match.setCitationExplanation(dto.getCitationExplanation());
                    match.setResourceCitationsJson(writeJsonSafely(dto.getResourceCitations()));
                    match.setPipelineVersion(version);
                    parseTaskIdeologyMatchMapper.insert(match);
                }
            }

            log.info("Projection persisted for taskId={} knowledgePoints={} ideologyMatches={}",
                    taskId,
                    result.getKnowledgePoints() == null ? 0 : result.getKnowledgePoints().size(),
                    result.getIdeologyMatches() == null ? 0 : result.getIdeologyMatches().size());
        } catch (Exception ex) {
            log.error("Failed to persist projections for taskId={}", taskId, ex);
        }
    }

    private List<ResourceCitationDto> sanitizeResourceCitations(JsonNode citationsNode) {
        List<ResourceCitationDto> citations = new ArrayList<>();
        if (citationsNode == null || !citationsNode.isArray()) {
            return citations;
        }
        int count = 0;
        for (JsonNode citationNode : citationsNode) {
            if (citationNode == null || !citationNode.isObject()) {
                continue;
            }
            pipelineJsonValidator.parseObject(
                    citationNode.toString(),
                    List.of("resourceRefId", "resourceId", "title", "source", "sourceUrl", "quotedExcerpt",
                            "citationReason"),
                    List.of("resourceRefId", "resourceId", "title", "source", "sourceUrl", "quotedExcerpt",
                            "citationReason"));
            ResourceCitationDto dto = objectMapper.convertValue(citationNode, ResourceCitationDto.class);
            dto.setResourceRefId(trimToLength(dto.getResourceRefId(), 20));
            dto.setTitle(trimToLength(dto.getTitle(), 200));
            dto.setSource(trimToLength(dto.getSource(), 120));
            dto.setSourceUrl(trimToLength(dto.getSourceUrl(), 500));
            dto.setQuotedExcerpt(trimToLength(dto.getQuotedExcerpt(), 300));
            dto.setCitationReason(trimToLength(dto.getCitationReason(), 300));
            citations.add(dto);
            count++;
            if (count >= 3) {
                break;
            }
        }
        return citations;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return safe(second);
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeText(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return "";
        }
        return node.asText().trim();
    }

    /**
     * 通过 {@link DocumentTextExtractor} 按扩展名真实抽取文本（PDF/Office），
     * 其余格式走纯文本读。失败时返回 null，上层允许回退到文件名推断。
     */
    private String readFileContent(String filePath) {
        return documentTextExtractor.extract(filePath);
    }

    /**
     * 调用 MinerU 解析本地文件；未配置或失败时返回 null，让上层静默降级。
     *
     * <p>
     * 这里刻意不把 MinerU 异常向上抛，因为降级到 {@link DocumentTextExtractor} 足以保障基础能力；
     * 失败原因写入 warnings 用于运维排查与前端 trace 展示。
     */
    private MineruParseClient.MineruParseResult tryMineruParse(
            ParseTask task, String fileName, List<String> warnings) {
        if (!mineruParseClient.isAvailable()) {
            warnings.add("MinerU disabled or not configured; using local extractor.");
            return null;
        }
        try {
            return mineruParseClient.parseLocalFile(task.getFilePath(), fileName);
        } catch (RuntimeException ex) {
            log.warn("MinerU parse failed for taskId={}, fallback to extractor: {}", task.getId(), ex.getMessage());
            warnings.add("MinerU parse failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * regenerate 场景下从已有 aiAnalysis JSON 里还原 DocumentStructureDto；拿不到时返回 null。
     */
    private DocumentStructureDto loadExistingStructure(ParseTask task) {
        String aiAnalysis = task.getAiAnalysis();
        if (aiAnalysis == null || aiAnalysis.isBlank()) {
            return null;
        }
        try {
            PipelineResultDto prev = objectMapper.readValue(aiAnalysis, PipelineResultDto.class);
            return prev.getDocumentStructure();
        } catch (Exception ex) {
            log.debug("Failed to reuse structure from aiAnalysis for taskId={}", task.getId());
            return null;
        }
    }

    /**
     * 尽力从已有任务中拿到 markdown 原文，优先取 structure.rawMarkdown，退化到 parsedContent。
     */
    private String extractExistingRawMarkdown(ParseTask task) {
        DocumentStructureDto existing = loadExistingStructure(task);
        if (existing != null && existing.getRawMarkdown() != null && !existing.getRawMarkdown().isBlank()) {
            return existing.getRawMarkdown();
        }
        String parsed = task.getParsedContent();
        if (parsed == null || parsed.isBlank()) {
            return null;
        }
        try {
            DocumentStructureDto parsedStructure = objectMapper.readValue(parsed, DocumentStructureDto.class);
            if (parsedStructure.getRawMarkdown() != null && !parsedStructure.getRawMarkdown().isBlank()) {
                return parsedStructure.getRawMarkdown();
            }
            return parsedStructure.getOverview();
        } catch (Exception ex) {
            return parsed;
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
