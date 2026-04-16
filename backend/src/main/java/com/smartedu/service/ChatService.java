package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.KnowledgeContextItem;
import com.smartedu.dto.SelectionExplainResponse;
import com.smartedu.entity.ChatMessage;
import com.smartedu.entity.ChatSession;
import com.smartedu.mapper.ChatMessageMapper;
import com.smartedu.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 对话服务。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_PROMPT = "You are the AI teaching assistant of the Smart Education Ideology platform. Help teachers design curriculum ideology teaching plans with concise, practical, and trustworthy answers.";

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AiIntelligenceService aiIntelligenceService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

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
    public ChatMessage sendMessage(Long sessionId, String userMessage) {
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

        String systemPrompt = buildPromptWithKnowledgeContext(userMessage);
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
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            session.setTitle(title);
        }
        chatSessionMapper.updateById(session);

        return aiMsg;
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
    private String buildPromptWithKnowledgeContext(String userMessage) {
        List<KnowledgeContextItem> contexts = knowledgeRetrievalService.retrieveContext(userMessage, 3, 3);
        if (contexts.isEmpty()) {
            return SYSTEM_PROMPT;
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\nPlease prioritize the following knowledge-base materials when answering. Include source links when relevant:\n");

        for (int i = 0; i < contexts.size(); i++) {
            KnowledgeContextItem item = contexts.get(i);
            context.append("[Source ").append(i + 1).append("]\n")
                    .append("Type: ").append(safe(item.getItemType())).append("\n")
                    .append("Title: ").append(safe(item.getTitle())).append("\n")
                    .append("Source: ").append(safe(item.getSource())).append("\n")
                    .append("Link: ").append(safe(item.getSourceUrl())).append("\n")
                    .append("Summary: ").append(truncate(safe(item.getSummary()), 220)).append("\n\n");
        }

        context.append("If the materials are insufficient, say so clearly and provide a general suggestion. ")
                .append("If a material is cited, include the corresponding source link when possible.");

        return SYSTEM_PROMPT + context;
    }

    /**
     * 统一提供选中文本解释能力。
     */
    public SelectionExplainResponse explainSelection(String selectionText) {
        List<KnowledgeContextItem> contexts = knowledgeRetrievalService.retrieveContext(selectionText, 4, 3);
        StringBuilder prompt = new StringBuilder();
        prompt.append("Explain the following selected text, then describe its relationship to curriculum ideology:\n")
                .append(selectionText)
                .append("\n\n");

        if (!contexts.isEmpty()) {
            prompt.append("Relevant knowledge-base context:\n");
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
                SYSTEM_PROMPT + " Use the knowledge-base context to explain the selected text, its ideological value, and any relevant sources.");
        return new SelectionExplainResponse(answer, contexts);
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
