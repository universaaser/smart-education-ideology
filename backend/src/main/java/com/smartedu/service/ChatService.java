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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 对话服务
 * 
 * <p>
 * 处理 AI 助手的对话会话和消息管理
 * 
 * @author SmartEducation Team
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AiIntelligenceService aiIntelligenceService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    /**
     * 思政教学助手的系统提示词
     */
    private static final String SYSTEM_PROMPT = "你是智教思政平台的AI教学助手，专门帮助教师进行课程思政教学设计。";

    /**
     * 获取用户的会话列表
     */
    public List<ChatSession> getUserSessions(Long userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getLastMessageAt);
        return chatSessionMapper.selectList(wrapper);
    }

    /**
     * 创建新会话
     */
    @Transactional
    public ChatSession createSession(Long userId, String title, String aiModel) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        session.setAiModel(aiModel);
        session.setMessageCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 获取会话的消息历史
     */
    public List<ChatMessage> getSessionMessages(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageMapper.selectList(wrapper);
    }

    /**
     * 发送消息并获取 AI 回复
     * 
     * @param sessionId   会话ID
     * @param userMessage 用户消息
     * @return AI 回复消息
     */
    @Transactional
    public ChatMessage sendMessage(Long sessionId, String userMessage) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }

        // 1. 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("USER");
        userMsg.setContent(userMessage);
        userMsg.setContentType("TEXT");
        userMsg.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(userMsg);

        // 2. 获取历史消息构建上下文
        List<ChatMessage> history = getSessionMessages(sessionId);
        List<Map<String, String>> messages = history.stream()
                .map(msg -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("role", msg.getRole().toLowerCase());
                    m.put("content", msg.getContent());
                    return m;
                })
                .collect(Collectors.toList());

        // 3. 将知识库资源注入系统提示词
        String systemPrompt = buildPromptWithKnowledgeContext(userMessage);

        // 4. 调用 AI 获取回复
        String aiResponse = aiIntelligenceService.chat(messages, systemPrompt);

        // 5. 保存 AI 回复
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("ASSISTANT");
        aiMsg.setContent(aiResponse);
        aiMsg.setContentType("TEXT");
        aiMsg.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(aiMsg);

        // 6. 更新会话信息
        session.setMessageCount(session.getMessageCount() + 2);
        session.setLastMessageAt(LocalDateTime.now());

        // 如果是第一条消息，用用户问题作为会话标题
        if (session.getMessageCount() == 2) {
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            session.setTitle(title);
        }

        chatSessionMapper.updateById(session);

        return aiMsg;
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        // 删除消息
        LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(ChatMessage::getSessionId, sessionId);
        chatMessageMapper.delete(msgWrapper);

        // 删除会话
        chatSessionMapper.deleteById(sessionId);
    }

    /**
     * 将抓取库内容拼接到系统提示词，提升回答准确性
     */
    private String buildPromptWithKnowledgeContext(String userMessage) {
        List<KnowledgeContextItem> contexts = knowledgeRetrievalService.retrieveContext(userMessage, 3, 3);
        if (contexts.isEmpty()) {
            return SYSTEM_PROMPT;
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n请优先参考以下知识库资料回答，必要时给出来源链接：\n");

        for (int i = 0; i < contexts.size(); i++) {
            KnowledgeContextItem item = contexts.get(i);
            context.append("[资料").append(i + 1).append("] ")
                    .append("类型：").append(safe(item.getItemType())).append("\n")
                    .append("标题：").append(safe(item.getTitle())).append("\n")
                    .append("来源：").append(safe(item.getSource())).append("\n")
                    .append("链接：").append(safe(item.getSourceUrl())).append("\n")
                    .append("摘要：").append(truncate(safe(item.getSummary()), 220)).append("\n\n");
        }

        context.append("回答时请优先依据上述资料；若资料不足，请明确说明并给出通用建议。")
                .append("如果引用资料，请尽量附上对应链接。");

        return SYSTEM_PROMPT + context;
    }

    /**
     * 为后续“选中文本解释”能力提供统一后端入口。
     *
     * <p>
     * 当前前端尚未接入，但接口已经可以直接复用统一检索服务和大模型问答能力。
     */
    public SelectionExplainResponse explainSelection(String selectionText) {
        List<KnowledgeContextItem> contexts = knowledgeRetrievalService.retrieveContext(selectionText, 4, 3);
        StringBuilder prompt = new StringBuilder();
        prompt.append("请解释以下选中文本的专业含义，并说明它与课程思政的关联：\n")
                .append(selectionText).append("\n\n");

        if (!contexts.isEmpty()) {
            prompt.append("可参考的知识库上下文：\n");
            for (KnowledgeContextItem context : contexts) {
                prompt.append("- 标题：").append(safe(context.getTitle())).append("\n")
                        .append("  摘要：").append(truncate(safe(context.getSummary()), 180)).append("\n")
                        .append("  来源：").append(safe(context.getSource())).append("\n")
                        .append("  链接：").append(safe(context.getSourceUrl())).append("\n");
            }
        }

        List<Map<String, String>> messages = new java.util.ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt.toString());
        messages.add(userMsg);

        String answer = aiIntelligenceService.chat(messages,
                SYSTEM_PROMPT + "请结合知识库上下文，对选中文本给出概念解释、思政价值和来源说明。");
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
