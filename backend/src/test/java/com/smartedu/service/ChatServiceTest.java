package com.smartedu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.KnowledgeContextItem;
import com.smartedu.dto.SelectionExplainRequestDto;
import com.smartedu.entity.SelectionExplainRecord;
import com.smartedu.mapper.ChatMessageMapper;
import com.smartedu.mapper.ChatSessionMapper;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.SelectionExplainRecordMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

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
        return new ChatService(
                (ChatSessionMapper) Proxy.newProxyInstance(
                        ChatSessionMapper.class.getClassLoader(),
                        new Class[]{ChatSessionMapper.class},
                        (proxy, method, args) -> null),
                (ChatMessageMapper) Proxy.newProxyInstance(
                        ChatMessageMapper.class.getClassLoader(),
                        new Class[]{ChatMessageMapper.class},
                        (proxy, method, args) -> null),
                buildSelectionExplainRecordMapper(store),
                buildAiServiceStub(),
                new StubKnowledgeRetrievalService(contexts),
                new ObjectMapper());
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
                null
        ) {
            @Override
            public String chat(List<java.util.Map<String, String>> messages, String systemPrompt) {
                return "Knowledge Evidence\nEvidence\n\nModel Reasoning\nReasoning";
            }
        };
    }

    private static class StubKnowledgeRetrievalService extends KnowledgeRetrievalService {

        private final List<KnowledgeContextItem> contexts;

        StubKnowledgeRetrievalService(List<KnowledgeContextItem> contexts) {
            super(null, null, null, null, null, null, null);
            this.contexts = contexts;
        }

        @Override
        public List<KnowledgeContextItem> retrieveContext(String query, int knowledgeLimit, int resourceLimit) {
            return contexts;
        }
    }

    private static class SelectionRecordStore {
        private final List<SelectionExplainRecord> records = new ArrayList<>();
    }
}
