package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.common.PageResult;
import com.smartedu.dto.SelectionExplainHistoryDto;
import com.smartedu.dto.SelectionExplainRequestDto;
import com.smartedu.dto.SelectionExplainResponse;
import com.smartedu.entity.ChatMessage;
import com.smartedu.entity.ChatSession;
import com.smartedu.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话控制器。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 获取用户会话列表。
     */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> getSessions(@RequestParam(defaultValue = "1") Long userId) {
        List<ChatSession> sessions = chatService.getUserSessions(userId);
        return Result.success(sessions);
    }

    /**
     * 创建新会话。
     */
    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.getOrDefault("userId", 1).toString());
        String title = (String) request.get("title");
        String aiModel = (String) request.getOrDefault("aiModel", "default");

        ChatSession session = chatService.createSession(userId, title, aiModel);
        return Result.success("Session created", session);
    }

    /**
     * 获取会话详情。
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<Map<String, Object>> getSessionDetail(@PathVariable Long sessionId) {
        List<ChatMessage> messages = chatService.getSessionMessages(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("messages", messages);
        return Result.success(result);
    }

    /**
     * 发送消息。
     */
    @PostMapping("/sessions/{sessionId}/message")
    public Result<ChatMessage> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.badRequest("Message cannot be empty");
        }

        try {
            ChatMessage reply = chatService.sendMessage(sessionId, message);
            return Result.success(reply);
        } catch (RuntimeException e) {
            return Result.error("AI response failed: " + e.getMessage());
        }
    }

    /**
     * 解释选中文本。
     */
    @PostMapping("/explain-selection")
    public Result<SelectionExplainResponse> explainSelection(@RequestBody SelectionExplainRequestDto request) {
        String text = request.getText();
        if (text == null || text.trim().isEmpty()) {
            return Result.badRequest("Text cannot be empty");
        }

        try {
            request.setText(text.trim());
            SelectionExplainResponse response = chatService.explainSelection(request);
            return Result.success(response);
        } catch (RuntimeException e) {
            return Result.error("Selection explanation failed: " + e.getMessage());
        }
    }

    /**
     * Query selected-text explanation history.
     */
    @GetMapping("/explain-selection/history")
    public Result<PageResult<SelectionExplainHistoryDto>> getSelectionExplainHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<SelectionExplainHistoryDto> history = chatService.getSelectionExplainHistory(
                userId,
                materialId,
                courseId,
                page == null ? 1 : page,
                size == null ? 10 : size);
        return Result.success(history);
    }

    /**
     * 删除会话。
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success("Session deleted", null);
    }
}
