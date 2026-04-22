package com.smartedu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.common.PageResult;
import com.smartedu.dto.ChatCitationDto;
import com.smartedu.dto.ChatResponseDto;
import com.smartedu.dto.SelectionExplainEvidenceDto;
import com.smartedu.dto.SelectionExplainHistoryDto;
import com.smartedu.dto.SelectionExplainRequestDto;
import com.smartedu.dto.SelectionExplainResponse;
import com.smartedu.entity.ChatMessage;
import com.smartedu.entity.ChatSession;
import com.smartedu.mapper.ChatMessageMapper;
import com.smartedu.mapper.ChatSessionMapper;
import com.smartedu.mapper.SelectionExplainRecordMapper;
import com.smartedu.service.AiIntelligenceService;
import com.smartedu.service.ChatService;
import com.smartedu.service.KnowledgeRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ChatController controller = new ChatController(new StubChatService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldRejectEmptySelectionText() throws Exception {
        mockMvc.perform(post("/api/chat/explain-selection")
                        .contentType("application/json")
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldRejectSelectionExplainWithoutUserId() throws Exception {
        mockMvc.perform(post("/api/chat/explain-selection")
                        .contentType("application/json")
                        .content("{\"text\":\"sensor network\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("User id cannot be empty"));
    }

    @Test
    void shouldExplainSelectionAndReturnEvidence() throws Exception {
        mockMvc.perform(post("/api/chat/explain-selection")
                        .contentType("application/json")
                        .content("{\"text\":\"sensor network\",\"userId\":7,\"courseId\":2,\"materialId\":3,\"parseTaskId\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recordId").value(9))
                .andExpect(jsonPath("$.data.hasReliableEvidence").value(true))
                .andExpect(jsonPath("$.data.evidenceItems[0].title").value("Sensor"));
    }

    @Test
    void shouldReturnSelectionExplainHistory() throws Exception {
        mockMvc.perform(get("/api/chat/explain-selection/history?userId=7&materialId=3&page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].recordId").value(9))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldCreateSessionFromStructuredRequest() throws Exception {
        mockMvc.perform(post("/api/chat/sessions")
                        .contentType("application/json")
                        .content("{\"userId\":7,\"title\":\"新对话\",\"aiModel\":\"openai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.aiModel").value("openai"));
    }

    @Test
    void shouldRejectSessionCreateWithoutUserId() throws Exception {
        mockMvc.perform(post("/api/chat/sessions")
                        .contentType("application/json")
                        .content("{\"title\":\"新对话\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("User id cannot be empty"));
    }

    @Test
    void shouldSendMessageAndReturnCitations() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/1/message")
                        .contentType("application/json")
                        .content("{\"message\":\"sensor network\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.message.content").value("answer"))
                .andExpect(jsonPath("$.data.retrievalStatus").value("FOUND"))
                .andExpect(jsonPath("$.data.citations[0].title").value("Sensor"));
    }

    private static class StubChatService extends ChatService {

        StubChatService() {
            super(
                    (ChatSessionMapper) Proxy.newProxyInstance(
                            ChatSessionMapper.class.getClassLoader(),
                            new Class[]{ChatSessionMapper.class},
                            (proxy, method, args) -> null),
                    (ChatMessageMapper) Proxy.newProxyInstance(
                            ChatMessageMapper.class.getClassLoader(),
                            new Class[]{ChatMessageMapper.class},
                            (proxy, method, args) -> null),
                    (SelectionExplainRecordMapper) Proxy.newProxyInstance(
                            SelectionExplainRecordMapper.class.getClassLoader(),
                            new Class[]{SelectionExplainRecordMapper.class},
                            (proxy, method, args) -> null),
                    (AiIntelligenceService) null,
                    (KnowledgeRetrievalService) null,
                    new ObjectMapper());
        }

        @Override
        public ChatResponseDto sendMessage(Long sessionId, String userMessage) {
            ChatMessage message = new ChatMessage();
            message.setId(11L);
            message.setSessionId(sessionId);
            message.setRole("ASSISTANT");
            message.setContent("answer");
            message.setContentType("TEXT");
            return new ChatResponseDto(
                    message,
                    List.of(new ChatCitationDto(
                            "SUBJECT_KNOWLEDGE",
                            1L,
                            "Sensor",
                            "Industrial sensor evidence",
                            "People Daily",
                            "https://example.com/sensor",
                            "FULLTEXT",
                            1.0D)),
                    "FOUND");
        }

        @Override
        public SelectionExplainResponse explainSelection(SelectionExplainRequestDto request) {
            SelectionExplainEvidenceDto evidence = new SelectionExplainEvidenceDto(
                    "SUBJECT_KNOWLEDGE",
                    1L,
                    "Sensor",
                    "Industrial sensor evidence",
                    "People Daily",
                    "https://example.com/sensor");
            SelectionExplainResponse response = new SelectionExplainResponse();
            response.setRecordId(9L);
            response.setAnswer("Knowledge Evidence\nSensor\n\nModel Reasoning\nReasoning");
            response.setModelReasoning(response.getAnswer());
            response.setHasReliableEvidence(true);
            response.setEvidenceItems(List.of(evidence));
            response.setContexts(List.of());
            response.setCreatedAt(LocalDateTime.now());
            return response;
        }

        @Override
        public PageResult<SelectionExplainHistoryDto> getSelectionExplainHistory(
                Long userId,
                Long materialId,
                Long courseId,
                int page,
                int size) {
            SelectionExplainHistoryDto item = new SelectionExplainHistoryDto();
            item.setRecordId(9L);
            item.setUserId(userId);
            item.setMaterialId(materialId);
            item.setSelectedText("sensor network");
            item.setAnswer("answer");
            item.setHasReliableEvidence(true);
            item.setEvidenceItems(List.of());
            item.setCreatedAt(LocalDateTime.now());
            return new PageResult<>(List.of(item), 1L, 10L, 1L);
        }

        @Override
        public ChatSession createSession(Long userId, String title, String aiModel) {
            ChatSession session = new ChatSession();
            session.setId(21L);
            session.setUserId(userId);
            session.setTitle(title);
            session.setAiModel(aiModel);
            return session;
        }
    }
}
