package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.AiProviderConfigDto;
import com.smartedu.dto.AiProviderConfigRequestDto;
import com.smartedu.dto.AiProviderTestResultDto;
import com.smartedu.dto.AiRouteConfigDto;
import com.smartedu.dto.AiRouteConfigRequestDto;
import com.smartedu.entity.AiProviderConfig;
import com.smartedu.entity.AiRouteConfig;
import com.smartedu.mapper.AiProviderConfigMapper;
import com.smartedu.mapper.AiRouteConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiProviderConfigService {

    private static final List<String> PROVIDER_KEYS = List.of("openai", "deepseek", "proxy", "gemini");
    private static final List<String> TASK_TYPES = List.of("chat", "parse", "ideology", "question-gen", "crawl", "path", "vision", "embedding");

    private final AiProviderConfigMapper providerMapper;
    private final AiRouteConfigMapper routeMapper;

    @Value("${ai.openai.enabled:false}")
    private boolean openaiEnabled;
    @Value("${ai.openai.label:openai}")
    private String openaiLabel;
    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;
    @Value("${ai.openai.base-url:}")
    private String openaiBaseUrl;
    @Value("${ai.openai.model:}")
    private String openaiModel;
    @Value("${ai.openai.timeout:120}")
    private int openaiTimeout;

    @Value("${ai.deepseek.enabled:false}")
    private boolean deepseekEnabled;
    @Value("${ai.deepseek.api-key:}")
    private String deepseekApiKey;
    @Value("${ai.deepseek.base-url:}")
    private String deepseekBaseUrl;
    @Value("${ai.deepseek.model:}")
    private String deepseekModel;
    @Value("${ai.deepseek.timeout:120}")
    private int deepseekTimeout;

    @Value("${ai.proxy.enabled:false}")
    private boolean proxyEnabled;
    @Value("${ai.proxy.label:default-proxy}")
    private String proxyLabel;
    @Value("${ai.proxy.api-key:}")
    private String proxyApiKey;
    @Value("${ai.proxy.base-url:}")
    private String proxyBaseUrl;
    @Value("${ai.proxy.model:}")
    private String proxyModel;
    @Value("${ai.proxy.timeout:120}")
    private int proxyTimeout;

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

    @Value("${ai.routes.chat:openai}")
    private String chatRoute;
    @Value("${ai.routes.parse:openai}")
    private String parseRoute;
    @Value("${ai.routes.ideology:openai}")
    private String ideologyRoute;
    @Value("${ai.routes.question-gen:openai}")
    private String questionRoute;
    @Value("${ai.routes.crawl:openai}")
    private String crawlRoute;
    @Value("${ai.routes.path:openai}")
    private String pathRoute;
    @Value("${ai.embedding.provider:openai}")
    private String embeddingRoute;

    public List<AiProviderConfigDto> listProviders() {
        Map<String, AiProviderConfig> saved = new LinkedHashMap<>();
        for (AiProviderConfig provider : providerMapper.selectList(new LambdaQueryWrapper<AiProviderConfig>())) {
            saved.put(provider.getProviderKey(), provider);
        }
        return PROVIDER_KEYS.stream()
                .map(key -> toProviderDto(saved.getOrDefault(key, defaultProvider(key))))
                .toList();
    }

    @Transactional
    public AiProviderConfigDto saveProvider(String providerKey, AiProviderConfigRequestDto request) {
        String key = normalize(providerKey);
        if (!PROVIDER_KEYS.contains(key) || request == null) {
            return null;
        }
        AiProviderConfig provider = findProvider(key);
        if (provider == null) {
            provider = defaultProvider(key);
            provider.setCreatedAt(LocalDateTime.now());
        }
        provider.setLabel(trim(firstNonBlank(request.getLabel(), provider.getLabel()), 100));
        if (request.getEnabled() != null) {
            provider.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        }
        provider.setApiBase(trim(request.getApiBase(), 500));
        provider.setModel(trim(request.getModel(), 100));
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            provider.setApiKey(trim(request.getApiKey(), 500));
        }
        provider.setTimeoutSeconds(request.getTimeoutSeconds() == null ? provider.getTimeoutSeconds() : clamp(request.getTimeoutSeconds(), 1, 300));
        provider.setUpdatedAt(LocalDateTime.now());
        if (provider.getId() == null) {
            providerMapper.insert(provider);
        } else {
            providerMapper.updateById(provider);
        }
        return toProviderDto(provider);
    }

    public List<AiRouteConfigDto> listRoutes() {
        Map<String, AiRouteConfig> saved = new LinkedHashMap<>();
        for (AiRouteConfig route : routeMapper.selectList(new LambdaQueryWrapper<AiRouteConfig>())) {
            saved.put(route.getTaskType(), route);
        }
        return TASK_TYPES.stream()
                .map(task -> toRouteDto(saved.getOrDefault(task, defaultRoute(task))))
                .toList();
    }

    @Transactional
    public AiRouteConfigDto saveRoute(String taskType, AiRouteConfigRequestDto request) {
        String task = normalize(taskType);
        if (!TASK_TYPES.contains(task) || request == null) {
            return null;
        }
        String providerKey = normalize(request.getProviderKey());
        if (!PROVIDER_KEYS.contains(providerKey)) {
            return null;
        }
        AiRouteConfig route = findRoute(task);
        if (route == null) {
            route = new AiRouteConfig();
            route.setTaskType(task);
            route.setCreatedAt(LocalDateTime.now());
        }
        route.setProviderKey(providerKey);
        route.setModel(trim(request.getModel(), 100));
        route.setUpdatedAt(LocalDateTime.now());
        if (route.getId() == null) {
            routeMapper.insert(route);
        } else {
            routeMapper.updateById(route);
        }
        return toRouteDto(route);
    }

    public AiProviderTestResultDto testProvider(String providerKey) {
        String key = normalize(providerKey);
        if (!PROVIDER_KEYS.contains(key)) {
            return new AiProviderTestResultDto(key, false, "Unsupported AI provider");
        }
        AiProviderConfig provider = findProvider(key);
        if (provider == null) {
            provider = defaultProvider(key);
        }
        if (!Integer.valueOf(1).equals(provider.getEnabled())) {
            return new AiProviderTestResultDto(key, false, "Provider is disabled");
        }
        if (isBlank(provider.getApiBase())) {
            return new AiProviderTestResultDto(key, false, "Provider base URL is missing");
        }
        if (isBlank(provider.getModel())) {
            return new AiProviderTestResultDto(key, false, "Provider model is missing");
        }
        if (isBlank(provider.getApiKey())) {
            return new AiProviderTestResultDto(key, false, "Provider API key is missing");
        }
        return new AiProviderTestResultDto(key, true, "Provider configuration is complete");
    }

    public boolean supportsProvider(String providerKey) {
        return PROVIDER_KEYS.contains(normalize(providerKey));
    }

    public boolean supportsTask(String taskType) {
        return TASK_TYPES.contains(normalize(taskType));
    }

    private AiProviderConfig findProvider(String providerKey) {
        LambdaQueryWrapper<AiProviderConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiProviderConfig::getProviderKey, providerKey).last("LIMIT 1");
        return providerMapper.selectOne(wrapper);
    }

    private AiRouteConfig findRoute(String taskType) {
        LambdaQueryWrapper<AiRouteConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiRouteConfig::getTaskType, taskType).last("LIMIT 1");
        return routeMapper.selectOne(wrapper);
    }

    private AiProviderConfig defaultProvider(String providerKey) {
        AiProviderConfig provider = new AiProviderConfig();
        provider.setProviderKey(providerKey);
        switch (providerKey) {
            case "deepseek" -> applyProvider(provider, "deepseek", deepseekEnabled, deepseekApiKey, deepseekBaseUrl, deepseekModel, deepseekTimeout);
            case "proxy" -> applyProvider(provider, proxyLabel, proxyEnabled, proxyApiKey, proxyBaseUrl, proxyModel, proxyTimeout);
            case "gemini" -> applyProvider(provider, "gemini", geminiEnabled, geminiApiKey, geminiBaseUrl, geminiModel, geminiTimeout);
            default -> applyProvider(provider, openaiLabel, openaiEnabled, openaiApiKey, openaiBaseUrl, openaiModel, openaiTimeout);
        }
        return provider;
    }

    private void applyProvider(AiProviderConfig provider, String label, boolean enabled, String apiKey, String apiBase, String model, int timeoutSeconds) {
        provider.setLabel(label);
        provider.setEnabled(enabled ? 1 : 0);
        provider.setApiKey(apiKey);
        provider.setApiBase(apiBase);
        provider.setModel(model);
        provider.setTimeoutSeconds(timeoutSeconds);
    }

    private AiRouteConfig defaultRoute(String taskType) {
        AiRouteConfig route = new AiRouteConfig();
        route.setTaskType(taskType);
        route.setProviderKey(switch (taskType) {
            case "chat" -> normalize(chatRoute);
            case "parse" -> normalize(parseRoute);
            case "ideology" -> normalize(ideologyRoute);
            case "question-gen" -> normalize(questionRoute);
            case "crawl" -> normalize(crawlRoute);
            case "path" -> normalize(pathRoute);
            case "embedding" -> normalize(embeddingRoute);
            default -> "openai";
        });
        route.setModel(defaultProvider(route.getProviderKey()).getModel());
        return route;
    }

    private AiProviderConfigDto toProviderDto(AiProviderConfig provider) {
        return new AiProviderConfigDto(
                provider.getId(),
                provider.getProviderKey(),
                provider.getLabel(),
                Integer.valueOf(1).equals(provider.getEnabled()),
                provider.getApiBase(),
                provider.getModel(),
                !isBlank(provider.getApiKey()),
                provider.getTimeoutSeconds(),
                provider.getCreatedAt(),
                provider.getUpdatedAt());
    }

    private AiRouteConfigDto toRouteDto(AiRouteConfig route) {
        return new AiRouteConfigDto(
                route.getId(),
                route.getTaskType(),
                route.getProviderKey(),
                route.getModel(),
                route.getCreatedAt(),
                route.getUpdatedAt());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String trim(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
