package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.common.PageResult;
import com.smartedu.dto.ChatCitationDto;
import com.smartedu.dto.ChatResponseDto;
import com.smartedu.dto.KnowledgeContextItem;
import com.smartedu.dto.KnowledgeRetrievalResult;
import com.smartedu.dto.SelectionExplainEvidenceDto;
import com.smartedu.dto.SelectionExplainHistoryDto;
import com.smartedu.dto.SelectionExplainRequestDto;
import com.smartedu.dto.SelectionExplainResponse;
import com.smartedu.dto.SemanticHitDto;
import com.smartedu.entity.ChatMessage;
import com.smartedu.entity.ChatSession;
import com.smartedu.entity.SelectionExplainRecord;
import com.smartedu.mapper.ChatMessageMapper;
import com.smartedu.mapper.ChatSessionMapper;
import com.smartedu.mapper.SelectionExplainRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 对话服务。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_PROMPT = "You are the AI teaching assistant of the Smart Education Ideology platform. Help teachers design curriculum ideology teaching plans with concise, practical, and trustworthy answers. Answer in plain text only. Do not use Markdown headings, bold markers, tables, code fences, or Markdown bullet syntax.";

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SelectionExplainRecordMapper selectionExplainRecordMapper;
    private final AiIntelligenceService aiIntelligenceService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final VectorIndexService vectorIndexService;
    private final VectorIndexAsyncService vectorIndexAsyncService;
    private final ObjectMapper objectMapper;

    /**
     * 获取用户会话列表。
     */
    public List<ChatSession> getUserSessions(Long userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getLastMessageAt);
        return chatSessionMapper.selectList(wrapper);
    }

    /**
     * 创建新会话。
     */
    @Transactional
    public ChatSession createSession(Long userId, String title, String aiModel) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null && !title.isBlank() ? title : "New Chat");
        session.setAiModel(aiModel != null && !aiModel.isBlank() ? aiModel : "default");
        session.setMessageCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 获取会话历史消息。
     */
    public List<ChatMessage> getSessionMessages(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageMapper.selectList(wrapper);
    }

    /**
     * 发送消息并获取 AI 回复。
     */
    @Transactional
    public ChatResponseDto sendMessage(Long sessionId, String userMessage) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("Chat session does not exist");
        }

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("USER");
        userMsg.setContent(userMessage);
        userMsg.setContentType("TEXT");
        userMsg.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(userMsg);

        List<ChatMessage> history = getSessionMessages(sessionId);
        List<Map<String, String>> messages = history.stream()
                .map(msg -> {
                    Map<String, String> item = new HashMap<>();
                    item.put("role", msg.getRole().toLowerCase());
                    item.put("content", msg.getContent());
                    return item;
                })
                .collect(Collectors.toList());

        KnowledgeRetrievalResult retrievalResult = retrieveChatContext(userMessage, 5);
        String systemPrompt = buildPromptWithKnowledgeContext(retrievalResult);
        String providerKey = session.getAiModel();
        String aiResponse = aiIntelligenceService.chat(messages, systemPrompt, providerKey);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("ASSISTANT");
        aiMsg.setContent(aiResponse);
        aiMsg.setContentType("TEXT");
        aiMsg.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(aiMsg);

        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
        session.setLastMessageAt(LocalDateTime.now());
        if (session.getMessageCount() == 2) {
            String title = generateSessionTitle(userMessage, aiResponse, providerKey);
            session.setTitle(title);
            session.setSummary(truncate(cleanPlainText(userMessage), 200));
        }
        chatSessionMapper.updateById(session);

        return new ChatResponseDto(
                aiMsg,
                retrievalResult.getContexts().stream().map(this::toChatCitation).collect(Collectors.toList()),
                retrievalResult.getRetrievalStatus());
    }

    private String generateSessionTitle(String userMessage, String aiResponse, String providerKey) {
        String fallback = truncate(cleanPlainText(userMessage), 24);
        if (fallback.isBlank()) {
            fallback = "New Chat";
        }
        try {
            List<Map<String, String>> titleMessages = List.of(Map.of(
                    "role", "user",
                    "content", "User message: " + truncate(cleanPlainText(userMessage), 300)
                            + "\nAssistant answer: " + truncate(cleanPlainText(aiResponse), 300)
                            + "\nCreate one short conversation title."));
            String title = aiIntelligenceService.chat(titleMessages,
                    "Create a short plain-text title for this conversation. No Markdown, no quotes, no punctuation decoration. Use 8 to 16 Chinese characters or 3 to 8 English words.",
                    providerKey);
            String cleaned = truncate(cleanPlainText(title), 60);
            return cleaned.isBlank() ? fallback : cleaned;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private String cleanPlainText(String value) {
        return safe(value)
                .replaceAll("[`*_#>|\\[\\]()]", "")
                .replaceAll("(?m)^\\s*[-+•]\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private KnowledgeRetrievalResult retrieveChatContext(String userMessage, int limit) {
        List<KnowledgeContextItem> vectorContexts = retrieveVectorContexts(userMessage, limit);
        if (!vectorContexts.isEmpty()) {
            return new KnowledgeRetrievalResult(KnowledgeRetrievalService.STATUS_FOUND, vectorContexts);
        }
        return knowledgeRetrievalService.retrieveWithStatus(userMessage, limit);
    }

    private List<KnowledgeContextItem> retrieveVectorContexts(String query, int limit) {
        List<KnowledgeContextItem> contexts = new ArrayList<>();
        contexts.addAll(searchVectorScope("knowledge_points", query, limit));
        if (contexts.size() < limit) {
            contexts.addAll(searchVectorScope("ideology_matches", query, limit - contexts.size()));
        }
        return deduplicateContexts(contexts).stream()
                .limit(Math.max(limit, 0))
                .collect(Collectors.toList());
    }

    private List<KnowledgeContextItem> searchVectorScope(String scope, String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String collection = vectorIndexService.resolveCollection(scope);
        return vectorIndexService.search(collection, query, limit, null).stream()
                .map(hit -> toVectorContext(hit, scope))
                .collect(Collectors.toList());
    }

    private List<KnowledgeContextItem> deduplicateContexts(List<KnowledgeContextItem> contexts) {
        Map<String, KnowledgeContextItem> unique = new LinkedHashMap<>();
        for (KnowledgeContextItem context : contexts) {
            String key = safe(context.getItemType()) + ":" + context.getReferenceId() + ":"
                    + safe(context.getTitle()) + ":" + safe(context.getSnippet());
            unique.putIfAbsent(key, context);
        }
        return new ArrayList<>(unique.values());
    }

    private KnowledgeContextItem toVectorContext(SemanticHitDto hit, String scope) {
        KnowledgeContextItem item = new KnowledgeContextItem(
                resolveVectorItemType(hit, scope),
                hit.getSourceId(),
                safe(hit.getTitle()),
                safe(hit.getSnippet()),
                safe(hit.getSource()),
                safe(hit.getSourceUrl()),
                scope);
        item.setSnippet(truncate(safe(hit.getSnippet()), 260));
        item.setMatchedBy("VECTOR");
        item.setScore(hit.getScore());
        return item;
    }

    private String resolveVectorItemType(SemanticHitDto hit, String scope) {
        return switch (safe(hit.getSourceType())) {
            case "parse_task_knowledge_point" -> "KNOWLEDGE_POINT";
            case "parse_task_ideology_match" -> "IDEOLOGY_MATCH";
            case "selection_explain_record" -> "SELECTION_EXPLAIN";
            default -> scope.toUpperCase();
        };
    }

    /**
     * 删除会话。
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(ChatMessage::getSessionId, sessionId);
        chatMessageMapper.delete(msgWrapper);
        chatSessionMapper.deleteById(sessionId);
    }

    /**
     * 将知识库上下文拼接到系统提示词中，提升回答的可追溯性。
     */
    private String buildPromptWithKnowledgeContext(KnowledgeRetrievalResult retrievalResult) {
        List<KnowledgeContextItem> contexts = retrievalResult.getContexts();
        if (contexts.isEmpty()) {
            return SYSTEM_PROMPT + "\n\nNo reliable knowledge-base context was retrieved. "
                    + "Clearly say that no reliable knowledge-base source was found before giving general teaching suggestions.";
        }

        StringBuilder context = new StringBuilder();
        if (KnowledgeRetrievalService.STATUS_WEAK_MATCH.equals(retrievalResult.getRetrievalStatus())) {
            context.append("\n\nKnowledge-base retrieval is weak. Say that the retrieved materials may be only partially related before answering.\n");
        }
        context.append("\n\nPlease prioritize the following knowledge-base materials when answering. ")
                .append("Do not invent source links. Include source links only from the provided materials:\n");

        for (int i = 0; i < contexts.size(); i++) {
            KnowledgeContextItem item = contexts.get(i);
            context.append("[Source ").append(i + 1).append("]\n")
                    .append("Type: ").append(safe(item.getItemType())).append("\n")
                    .append("Title: ").append(safe(item.getTitle())).append("\n")
                    .append("Source: ").append(safe(item.getSource())).append("\n")
                    .append("Link: ").append(safe(item.getSourceUrl())).append("\n")
                    .append("Snippet: ").append(truncate(firstNonBlank(item.getSnippet(), item.getSummary()), 260)).append("\n")
                    .append("Matched By: ").append(safe(item.getMatchedBy())).append("\n\n");
        }

        context.append("If the materials are insufficient, say so clearly and provide a general suggestion. ")
                .append("If a material is cited, include the corresponding source link when possible.");

        return SYSTEM_PROMPT + context;
    }

    private ChatCitationDto toChatCitation(KnowledgeContextItem item) {
        return new ChatCitationDto(
                safe(item.getItemType()),
                item.getReferenceId(),
                safe(item.getTitle()),
                truncate(firstNonBlank(item.getSnippet(), item.getSummary()), 220),
                safe(item.getSource()),
                safe(item.getSourceUrl()),
                safe(item.getMatchedBy()),
                item.getScore());
    }

    /**
     * 统一提供选中文本解释能力。
     */
    @Transactional
    public SelectionExplainResponse explainSelection(SelectionExplainRequestDto request) {
        // Keep a service-layer guard so future internal callers cannot reintroduce a fallback user.
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("User id cannot be empty");
        }
        String selectionText = safe(request.getText()).trim();
        List<KnowledgeContextItem> contexts = knowledgeRetrievalService.retrieveContext(selectionText, 4, 3);
        List<SelectionExplainEvidenceDto> evidenceItems = contexts.stream()
                .map(this::toEvidenceItem)
                .collect(Collectors.toList());
        boolean hasReliableEvidence = !evidenceItems.isEmpty();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Explain the following selected text for a teacher preparing curriculum ideology materials.\n")
                .append("Return the answer with two clear sections named Knowledge Evidence and Model Reasoning.\n")
                .append("Knowledge Evidence must only use the provided knowledge-base context. ")
                .append("Model Reasoning must explain the selected text based on those sources. ")
                .append("If no knowledge-base context is provided, say that no reliable knowledge-base evidence was found.\n\n")
                .append("Selected text:\n")
                .append(selectionText)
                .append("\n\n");

        if (!contexts.isEmpty()) {
            prompt.append("Knowledge-base context:\n");
            for (KnowledgeContextItem context : contexts) {
                prompt.append("- Title: ").append(safe(context.getTitle())).append("\n")
                        .append("  Summary: ").append(truncate(safe(context.getSummary()), 180)).append("\n")
                        .append("  Source: ").append(safe(context.getSource())).append("\n")
                        .append("  Link: ").append(safe(context.getSourceUrl())).append("\n");
            }
        }

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt.toString());
        messages.add(userMsg);

        String answer = aiIntelligenceService.chat(
                messages,
                SYSTEM_PROMPT + " Keep knowledge-base evidence separate from model reasoning. Never invent source links.");
        String modelReasoning = extractModelReasoning(answer);

        SelectionExplainRecord record = new SelectionExplainRecord();
        record.setUserId(request.getUserId());
        record.setCourseId(request.getCourseId());
        record.setMaterialId(request.getMaterialId());
        record.setParseTaskId(request.getParseTaskId());
        record.setSelectedText(selectionText);
        record.setAnswer(answer);
        record.setModelReasoning(modelReasoning);
        record.setEvidenceJson(writeEvidenceJson(evidenceItems));
        record.setHasReliableEvidence(hasReliableEvidence ? 1 : 0);
        record.setCreatedAt(LocalDateTime.now());
        selectionExplainRecordMapper.insert(record);
        vectorIndexAsyncService.indexSelectionExplainRecord(record);

        SelectionExplainResponse response = new SelectionExplainResponse();
        response.setRecordId(record.getId());
        response.setAnswer(answer);
        response.setModelReasoning(modelReasoning);
        response.setHasReliableEvidence(hasReliableEvidence);
        response.setEvidenceItems(evidenceItems);
        response.setContexts(contexts);
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    public PageResult<SelectionExplainHistoryDto> getSelectionExplainHistory(
            Long userId,
            Long materialId,
            Long courseId,
            int page,
            int size) {
        Page<SelectionExplainRecord> pageParam = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<SelectionExplainRecord> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(SelectionExplainRecord::getUserId, userId);
        }
        if (materialId != null) {
            wrapper.eq(SelectionExplainRecord::getMaterialId, materialId);
        }
        if (courseId != null) {
            wrapper.eq(SelectionExplainRecord::getCourseId, courseId);
        }
        wrapper.orderByDesc(SelectionExplainRecord::getCreatedAt);

        Page<SelectionExplainRecord> result = selectionExplainRecordMapper.selectPage(pageParam, wrapper);
        List<SelectionExplainHistoryDto> records = result.getRecords().stream()
                .map(this::toHistoryDto)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getSize(), result.getCurrent());
    }

    private SelectionExplainEvidenceDto toEvidenceItem(KnowledgeContextItem context) {
        return new SelectionExplainEvidenceDto(
                safe(context.getItemType()),
                context.getReferenceId(),
                safe(context.getTitle()),
                safe(context.getSummary()),
                safe(context.getSource()),
                safe(context.getSourceUrl()));
    }

    private SelectionExplainHistoryDto toHistoryDto(SelectionExplainRecord record) {
        SelectionExplainHistoryDto dto = new SelectionExplainHistoryDto();
        dto.setRecordId(record.getId());
        dto.setUserId(record.getUserId());
        dto.setCourseId(record.getCourseId());
        dto.setMaterialId(record.getMaterialId());
        dto.setParseTaskId(record.getParseTaskId());
        dto.setSelectedText(record.getSelectedText());
        dto.setAnswer(record.getAnswer());
        dto.setModelReasoning(record.getModelReasoning());
        dto.setHasReliableEvidence(Integer.valueOf(1).equals(record.getHasReliableEvidence()));
        dto.setEvidenceItems(readEvidenceItems(record.getEvidenceJson()));
        dto.setCreatedAt(record.getCreatedAt());
        return dto;
    }

    private String writeEvidenceJson(List<SelectionExplainEvidenceDto> evidenceItems) {
        try {
            return objectMapper.writeValueAsString(evidenceItems);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<SelectionExplainEvidenceDto> readEvidenceItems(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(evidenceJson, new TypeReference<List<SelectionExplainEvidenceDto>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String extractModelReasoning(String answer) {
        String safeAnswer = safe(answer);
        String marker = "Model Reasoning";
        int markerIndex = safeAnswer.toLowerCase().indexOf(marker.toLowerCase());
        if (markerIndex < 0) {
            return safeAnswer;
        }
        String reasoning = safeAnswer.substring(markerIndex + marker.length())
                .replaceFirst("^[\\s:：#-]+", "")
                .trim();
        return reasoning.isBlank() ? safeAnswer : reasoning;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return safe(second);
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }
}
