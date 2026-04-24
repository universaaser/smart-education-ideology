package com.smartedu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.SemanticHitDto;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Phase 3A: Qdrant vector index service.
 *
 * <p>
 * Provides embedding generation (via OpenAI-compatible API) and vector CRUD against Qdrant REST API.
 * Falls back to empty results when Qdrant or embedding service is unavailable.
 */
@Slf4j
@Service
public class VectorIndexService {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${qdrant.enabled:false}")
    private boolean qdrantEnabled;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Value("${qdrant.collection-knowledge-points:knowledge_points}")
    private String collectionKnowledgePoints;

    @Value("${qdrant.collection-ideology-matches:ideology_matches}")
    private String collectionIdeologyMatches;

    @Value("${qdrant.collection-selection-explain:selection_explain}")
    private String collectionSelectionExplain;

    @Value("${ai.embedding.enabled:false}")
    private boolean embeddingEnabled;

    @Value("${ai.embedding.provider:openai}")
    private String embeddingProvider;

    @Value("${ai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${ai.embedding.dimensions:1536}")
    private int embeddingDimensions;

    @Value("${ai.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${ai.embedding.base-url:}")
    private String embeddingBaseUrl;

    @Value("${ai.openai.base-url:http://localhost:8317/v1}")
    private String openaiBaseUrl;

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.embedding.timeout:30}")
    private int embeddingTimeout;

    public VectorIndexService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generate embedding vector for a single text.
     */
    public float[] embed(String text) {
        if (!embeddingEnabled) {
            return null;
        }
        List<float[]> batch = embedBatch(Collections.singletonList(text));
        if (batch == null || batch.isEmpty()) {
            return null;
        }
        return batch.get(0);
    }

    /**
     * Batch embedding. Returns null on failure so callers can degrade gracefully.
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (!embeddingEnabled || texts == null || texts.isEmpty()) {
            return null;
        }
        String baseUrl = resolveEmbeddingBaseUrl();
        String apiKey = resolveEmbeddingApiKey();
        String url = baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";

        Map<String, Object> body = new HashMap<>();
        body.put("model", embeddingModel);
        body.put("input", texts);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(writeJson(body), JSON));
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Embedding API returned {} for {}", response.code(), url);
                return null;
            }
            return parseEmbeddings(response.body().string());
        } catch (IOException ex) {
            log.error("Embedding API call failed: {}", url, ex);
            return null;
        }
    }

    /**
     * Upsert a single vector record into Qdrant. Creates collection if missing.
     */
    public void upsert(String collection, String id, float[] vector, Map<String, Object> payload) {
        if (!qdrantEnabled || vector == null || id == null) {
            return;
        }
        try {
            ensureCollection(collection, vector.length);
            String url = qdrantUrl("/collections/" + collection + "/points");

            Map<String, Object> point = new HashMap<>();
            point.put("id", id);
            point.put("vector", vector);
            if (payload != null) {
                point.put("payload", payload);
            }
            Map<String, Object> body = new HashMap<>();
            body.put("points", Collections.singletonList(point));

            Request request = new Request.Builder()
                    .url(url)
                    .put(RequestBody.create(writeJson(body), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Qdrant upsert failed: {} {}", response.code(), url);
                }
            }
        } catch (Exception ex) {
            log.error("Qdrant upsert failed for collection={} id={}", collection, id, ex);
        }
    }

    /**
     * Delete indexed records by logical source marker.
     */
    public void deleteBySource(String collection, String sourceType, Long sourceId) {
        if (!qdrantEnabled || sourceId == null || sourceType == null || sourceType.isBlank()) {
            return;
        }
        try {
            String url = qdrantUrl("/collections/" + collection + "/points/delete");
            Map<String, Object> sourceTypeCondition = Map.of("key", "source_type", "match", Map.of("value", sourceType));
            Map<String, Object> sourceIdCondition = Map.of("key", "source_id", "match", Map.of("value", sourceId));
            Map<String, Object> body = Map.of(
                    "filter",
                    Map.of("must", List.of(sourceTypeCondition, sourceIdCondition)));

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(writeJson(body), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Qdrant delete failed: {} {}", response.code(), url);
                }
            }
        } catch (Exception ex) {
            log.error("Qdrant delete failed for collection={} sourceType={} sourceId={}",
                    collection, sourceType, sourceId, ex);
        }
    }

    /**
     * Semantic search by raw query text. End-to-end: embed -> qdrant search -> hits.
     */
    public List<SemanticHitDto> search(String collection, String query, int topK, Map<String, Object> filter) {
        float[] vector = embed(query);
        if (vector == null) {
            return Collections.emptyList();
        }
        return searchByVector(collection, vector, topK, filter);
    }

    /**
     * Search Qdrant by an existing vector.
     */
    public List<SemanticHitDto> searchByVector(String collection, float[] vector, int topK, Map<String, Object> filter) {
        if (!qdrantEnabled || vector == null) {
            return Collections.emptyList();
        }
        try {
            String url = qdrantUrl("/collections/" + collection + "/points/search");
            Map<String, Object> body = new HashMap<>();
            body.put("vector", vector);
            body.put("limit", topK);
            body.put("with_payload", true);
            if (filter != null && !filter.isEmpty()) {
                body.put("filter", filter);
            }

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(writeJson(body), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Qdrant search failed: {} {}", response.code(), url);
                    return Collections.emptyList();
                }
                return parseSearchHits(response.body().string());
            }
        } catch (Exception ex) {
            log.error("Qdrant search failed for collection={}", collection, ex);
            return Collections.emptyList();
        }
    }

    /**
     * Map scope name to configured collection name.
     */
    public String resolveCollection(String scope) {
        return switch (scope) {
            case "knowledge_points" -> collectionKnowledgePoints;
            case "ideology_matches" -> collectionIdeologyMatches;
            case "selection_explain" -> collectionSelectionExplain;
            default -> collectionKnowledgePoints;
        };
    }

    private void ensureCollection(String collection, int size) {
        String url = qdrantUrl("/collections/" + collection);
        Request check = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(check).execute()) {
            if (response.isSuccessful()) {
                return;
            }
        } catch (IOException ex) {
            log.debug("Collection check failed, will try to create: {}", ex.getMessage());
        }

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> vectors = new HashMap<>();
        vectors.put("size", size);
        vectors.put("distance", "Cosine");
        body.put("vectors", vectors);

        Request create = new Request.Builder()
                .url(url)
                .put(RequestBody.create(writeJson(body), JSON))
                .build();
        try (Response response = httpClient.newCall(create).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Qdrant create collection failed: {} {}", response.code(), url);
            }
        } catch (IOException ex) {
            log.error("Qdrant create collection failed: {}", url, ex);
        }
    }

    private String qdrantUrl(String path) {
        return "http://" + qdrantHost + ":" + qdrantPort + path;
    }

    private String resolveEmbeddingBaseUrl() {
        if (embeddingBaseUrl != null && !embeddingBaseUrl.isBlank()) {
            return embeddingBaseUrl;
        }
        if ("openai".equalsIgnoreCase(embeddingProvider)) {
            return openaiBaseUrl;
        }
        return openaiBaseUrl;
    }

    private String resolveEmbeddingApiKey() {
        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) {
            return embeddingApiKey;
        }
        if ("openai".equalsIgnoreCase(embeddingProvider)) {
            return openaiApiKey;
        }
        return openaiApiKey;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize JSON", ex);
        }
    }

    private List<float[]> parseEmbeddings(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            return null;
        }
        List<float[]> result = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                result.add(null);
                continue;
            }
            float[] vec = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vec[i] = (float) embedding.get(i).asDouble();
            }
            result.add(vec);
        }
        return result;
    }

    private List<SemanticHitDto> parseSearchHits(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.get("result");
        if (results == null || !results.isArray()) {
            return Collections.emptyList();
        }
        List<SemanticHitDto> hits = new ArrayList<>();
        for (JsonNode item : results) {
            SemanticHitDto hit = new SemanticHitDto();
            hit.setId(item.path("id").asText());
            hit.setScore(item.path("score").asDouble(0.0));
            JsonNode payload = item.get("payload");
            if (payload != null) {
                hit.setTitle(payload.path("title").asText());
                hit.setSnippet(payload.path("snippet").asText());
                hit.setSourceType(payload.path("source_type").asText());
                hit.setSource(payload.path("source").asText());
                hit.setSourceUrl(payload.path("source_url").asText());
                if (payload.has("source_id") && payload.get("source_id").isIntegralNumber()) {
                    hit.setSourceId(payload.get("source_id").asLong());
                }
            }
            hits.add(hit);
        }
        return hits;
    }
}
