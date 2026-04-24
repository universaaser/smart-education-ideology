package com.smartedu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.KnowledgeContextItem;
import com.smartedu.dto.SelectionExplainRequestDto;
import com.smartedu.dto.SemanticHitDto;
import com.smartedu.entity.ChatMessage;
import com.smartedu.entity.ChatSession;
import com.smartedu.entity.SelectionExplainRecord;
import com.smartedu.mapper.ChatMessageMapper;
import com.smartedu.mapper.ChatSessionMapper;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.SelectionExplainRecordMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {

    @Test
    void shouldSaveSelectionExplanationWithEvidence() {
        SelectionRecordStore store = new SelectionRecordStore();
        ChatService service = buildService(store, List.of(new KnowledgeContextItem(
                "SUBJECT_KNOWLEDGE",
                1L,
                "Sensor",
                "Industrial sensor summary",
                "People Daily",
                "https://example.com/sensor",
                "TECH")));

        SelectionExplainRequestDto request = new SelectionExplainRequestDto();
        request.setText("sensor network");
        request.setUserId(7L);
        request.setCourseId(2L);
        request.setMaterialId(3L);
        request.setParseTaskId(4L);

        var response = service.explainSelection(request);

        assertNotNull(response.getRecordId());
        assertTrue(response.getHasReliableEvidence());
        assertEquals(1, response.getEvidenceItems().size());
        assertEquals(1, store.records.size());
        assertEquals(1, store.records.get(0).getHasReliableEvidence());
        assertEquals(1, store.indexedRecords.size());
        assertEquals(store.records.get(0).getId(), store.indexedRecords.get(0).getId());
    }

    @Test
    void shouldMarkExplanationWithoutEvidence() {
        SelectionRecordStore store = new SelectionRecordStore();
        ChatService service = buildService(store, List.of());

        SelectionExplainRequestDto request = new SelectionExplainRequestDto();
        request.setText("unknown concept");
        request.setUserId(8L);

        var response = service.explainSelection(request);

        assertFalse(response.getHasReliableEvidence());
        assertEquals(0, response.getEvidenceItems().size());
        assertEquals(0, store.records.get(0).getHasReliableEvidence());
        assertEquals(8L, store.records.get(0).getUserId());
        assertEquals(1, store.indexedRecords.size());
    }

    @Test
    void shouldUseVectorContextBeforeFallbackRetrievalForChat() {
        SelectionRecordStore store = new SelectionRecordStore();
        ChatService service = buildService(
                store,
                List.of(new KnowledgeContextItem("SUBJECT_KNOWLEDGE", 9L, "Fallback", "Fallback summary", "", "", "TECH")),
                List.of(new SemanticHitDto(
                        "hit-1",
                        0.91D,
                        "Vector Sensor",
                        "Vector evidence",
                        "parse_task_knowledge_point",
                        1L,
                        "People Daily",
                        "https://example.com/vector-sensor")));

        var response = service.sendMessage(1L, "sensor network");

        assertEquals("FOUND", response.getRetrievalStatus());
        assertEquals(1, response.getCitations().size());
        assertEquals("Vector Sensor", response.getCitations().get(0).getTitle());
        assertEquals("People Daily", response.getCitations().get(0).getSource());
        assertEquals("https://example.com/vector-sensor", response.getCitations().get(0).getSourceUrl());
        assertEquals("VECTOR", response.getCitations().get(0).getMatchedBy());
        assertEquals(0, store.fallbackRetrievalCount);
    }

    @Test
    void shouldFallbackToLightweightRetrievalWhenVectorContextIsEmpty() {
        SelectionRecordStore store = new SelectionRecordStore();
        ChatService service = buildService(
                store,
                List.of(new KnowledgeContextItem("SUBJECT_KNOWLEDGE", 9L, "Fallback", "Fallback summary", "", "", "TECH")),
                List.of());

        var response = service.sendMessage(1L, "sensor network");

        assertEquals("FOUND", response.getRetrievalStatus());
        assertEquals("Fallback", response.getCitations().get(0).getTitle());
        assertEquals(1, store.fallbackRetrievalCount);
    }

    @Test
    void shouldReturnSelectionExplanationHistory() {
        SelectionRecordStore store = new SelectionRecordStore();
        SelectionExplainRecord record = new SelectionExplainRecord();
        record.setId(5L);
        record.setUserId(7L);
        record.setMaterialId(3L);
        record.setSelectedText("sensor network");
        record.setAnswer("answer");
        record.setModelReasoning("answer");
        record.setEvidenceJson("[]");
        record.setHasReliableEvidence(1);
        store.records.add(record);

        ChatService service = buildService(store, List.of());
        var history = service.getSelectionExplainHistory(7L, 3L, null, 1, 10);

        assertEquals(1, history.getTotal());
        assertEquals(5L, history.getRecords().get(0).getRecordId());
        assertTrue(history.getRecords().get(0).getHasReliableEvidence());
    }

    private ChatService buildService(SelectionRecordStore store, List<KnowledgeContextItem> contexts) {
        return buildService(store, contexts, List.of());
    }

    private ChatService buildService(
            SelectionRecordStore store,
            List<KnowledgeContextItem> contexts,
            List<SemanticHitDto> vectorHits) {
        return new ChatService(
                buildChatSessionMapper(),
                buildChatMessageMapper(store),
                buildSelectionExplainRecordMapper(store),
                buildAiServiceStub(),
                new StubKnowledgeRetrievalService(store, contexts),
                new StubVectorIndexService(vectorHits),
                new RecordingVectorIndexAsyncService(store),
                new ObjectMapper());
    }

    private ChatSessionMapper buildChatSessionMapper() {
        return (ChatSessionMapper) Proxy.newProxyInstance(
                ChatSessionMapper.class.getClassLoader(),
                new Class[]{ChatSessionMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        ChatSession session = new ChatSession();
                        session.setId((Long) args[0]);
                        session.setUserId(7L);
                        session.setTitle("Chat");
                        session.setAiModel("openai");
                        session.setMessageCount(0);
                        return session;
                    }
                    return primitiveDefault(method.getReturnType());
                });
    }

    private ChatMessageMapper buildChatMessageMapper(SelectionRecordStore store) {
        return (ChatMessageMapper) Proxy.newProxyInstance(
                ChatMessageMapper.class.getClassLoader(),
                new Class[]{ChatMessageMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName()) && args[0] instanceof ChatMessage message) {
                        message.setId((long) store.chatMessages.size() + 1);
                        store.chatMessages.add(message);
                        return 1;
                    }
                    if ("selectList".equals(method.getName())) {
                        return store.chatMessages;
                    }
                    return primitiveDefault(method.getReturnType());
                });
    }

    private SelectionExplainRecordMapper buildSelectionExplainRecordMapper(SelectionRecordStore store) {
        return (SelectionExplainRecordMapper) Proxy.newProxyInstance(
                SelectionExplainRecordMapper.class.getClassLoader(),
                new Class[]{SelectionExplainRecordMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName()) && args[0] instanceof SelectionExplainRecord record) {
                        record.setId((long) store.records.size() + 1);
                        store.records.add(record);
                        return 1;
                    }
                    if ("selectPage".equals(method.getName()) && args[0] instanceof Page<?> page) {
                        @SuppressWarnings("unchecked")
                        Page<SelectionExplainRecord> typedPage = (Page<SelectionExplainRecord>) page;
                        typedPage.setRecords(store.records);
                        typedPage.setTotal(store.records.size());
                        return typedPage;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }

    private AiIntelligenceService buildAiServiceStub() {
        return new AiIntelligenceService(
                (ParseTaskMapper) Proxy.newProxyInstance(
                        ParseTaskMapper.class.getClassLoader(),
                        new Class[]{ParseTaskMapper.class},
                        (proxy, method, args) -> null),
                new ObjectMapper(),
                null,
                new AiPipelineJsonValidator(new ObjectMapper()),
                null,
                null,
                new DocumentTextExtractor(),
                new MineruParseClient(new ObjectMapper()),
                new AiStreamBuffer(),
                emptyKnowledgePointMapper(),
                emptyIdeologyMatchMapper(),
                null
        ) {
            @Override
            public String chat(List<java.util.Map<String, String>> messages, String systemPrompt) {
                return "Knowledge Evidence\nEvidence\n\nModel Reasoning\nReasoning";
            }

            @Override
            public String chat(List<java.util.Map<String, String>> messages, String systemPrompt, String providerKey) {
                return "Knowledge Evidence\nEvidence\n\nModel Reasoning\nReasoning";
            }
        };
    }

    private ParseTaskKnowledgePointMapper emptyKnowledgePointMapper() {
        return (ParseTaskKnowledgePointMapper) Proxy.newProxyInstance(
                ParseTaskKnowledgePointMapper.class.getClassLoader(),
                new Class[]{ParseTaskKnowledgePointMapper.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
    }

    private ParseTaskIdeologyMatchMapper emptyIdeologyMatchMapper() {
        return (ParseTaskIdeologyMatchMapper) Proxy.newProxyInstance(
                ParseTaskIdeologyMatchMapper.class.getClassLoader(),
                new Class[]{ParseTaskIdeologyMatchMapper.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
    }

    private Object primitiveDefault(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.isPrimitive()) {
            return 0;
        }
        return null;
    }

    private static class StubKnowledgeRetrievalService extends KnowledgeRetrievalService {

        private final SelectionRecordStore store;
        private final List<KnowledgeContextItem> contexts;

        StubKnowledgeRetrievalService(SelectionRecordStore store, List<KnowledgeContextItem> contexts) {
            super(null, null, null, null, null, null, null);
            this.store = store;
            this.contexts = contexts;
        }

        @Override
        public List<KnowledgeContextItem> retrieveContext(String query, int knowledgeLimit, int resourceLimit) {
            return contexts;
        }

        @Override
        public com.smartedu.dto.KnowledgeRetrievalResult retrieveWithStatus(String query, int limit) {
            store.fallbackRetrievalCount++;
            return new com.smartedu.dto.KnowledgeRetrievalResult(
                    contexts.isEmpty() ? STATUS_NO_CONTEXT : STATUS_FOUND,
                    contexts);
        }
    }

    private static class StubVectorIndexService extends VectorIndexService {

        private final List<SemanticHitDto> hits;

        StubVectorIndexService(List<SemanticHitDto> hits) {
            super(new ObjectMapper());
            this.hits = hits;
        }

        @Override
        public String resolveCollection(String scope) {
            return scope;
        }

        @Override
        public List<SemanticHitDto> search(String collection, String query, int topK, Map<String, Object> filter) {
            return hits.stream()
                    .filter(hit -> matchesCollection(collection, hit.getSourceType()))
                    .limit(Math.max(topK, 0))
                    .toList();
        }

        private boolean matchesCollection(String collection, String sourceType) {
            return ("knowledge_points".equals(collection) && "parse_task_knowledge_point".equals(sourceType))
                    || ("ideology_matches".equals(collection) && "parse_task_ideology_match".equals(sourceType));
        }
    }

    private static class SelectionRecordStore {
        private final List<SelectionExplainRecord> records = new ArrayList<>();
        private final List<SelectionExplainRecord> indexedRecords = new ArrayList<>();
        private final List<ChatMessage> chatMessages = new ArrayList<>();
        private int fallbackRetrievalCount;
    }

    private static class RecordingVectorIndexAsyncService extends VectorIndexAsyncService {

        private final SelectionRecordStore store;

        RecordingVectorIndexAsyncService(SelectionRecordStore store) {
            super(new VectorIndexService(new ObjectMapper()));
            this.store = store;
        }

        @Override
        public void indexSelectionExplainRecord(SelectionExplainRecord record) {
            store.indexedRecords.add(record);
        }
    }
}
