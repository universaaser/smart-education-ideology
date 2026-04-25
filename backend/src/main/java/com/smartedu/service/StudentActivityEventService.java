package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.StudentEventBatchRequestDto;
import com.smartedu.dto.StudentEventDto;
import com.smartedu.dto.StudentLearningReportDto;
import com.smartedu.dto.StudentRecentActivityDto;
import com.smartedu.entity.ChatSession;
import com.smartedu.mapper.ChatSessionMapper;
import com.smartedu.entity.StudentActivityEvent;
import com.smartedu.mapper.StudentActivityEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentActivityEventService {

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "page_stay",
            "material_open",
            "knowledge_view",
            "ai_ask",
            "answer_submit",
            "path_switch"
    );

    private final StudentActivityEventMapper studentActivityEventMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;

    public int saveBatch(StudentEventBatchRequestDto request) {
        List<StudentActivityEvent> events = new ArrayList<>();
        for (StudentEventDto item : request.getEvents()) {
            if (item == null || item.getEventType() == null || !SUPPORTED_EVENT_TYPES.contains(item.getEventType())) {
                continue;
            }
            StudentActivityEvent event = new StudentActivityEvent();
            event.setStudentId(request.getStudentId());
            event.setCourseId(request.getCourseId());
            event.setEventType(item.getEventType());
            event.setKnowledgePointId(item.getKnowledgePointId());
            event.setDurationSeconds(item.getDurationSeconds());
            event.setPayloadJson(writePayload(item.getPayload()));
            event.setOccurredAt(item.getOccurredAt() == null ? LocalDateTime.now() : item.getOccurredAt());
            events.add(event);
        }
        for (StudentActivityEvent event : events) {
            studentActivityEventMapper.insert(event);
        }
        return events.size();
    }

    public StudentLearningReportDto getReport(Long studentId, Long courseId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        List<StudentActivityEvent> todayEvents = studentActivityEventMapper.selectList(baseQuery(studentId, courseId)
                .ge(StudentActivityEvent::getOccurredAt, start)
                .le(StudentActivityEvent::getOccurredAt, end));

        int todayStudyMinutes = todayEvents.stream()
                .filter(event -> event.getDurationSeconds() != null)
                .mapToInt(StudentActivityEvent::getDurationSeconds)
                .sum() / 60;
        int totalStudyMinutes = studentActivityEventMapper.selectList(baseQuery(studentId, courseId))
                .stream()
                .filter(event -> event.getDurationSeconds() != null)
                .mapToInt(StudentActivityEvent::getDurationSeconds)
                .sum() / 60;
        long knowledgeViewCount = todayEvents.stream()
                .filter(event -> "knowledge_view".equals(event.getEventType()))
                .count();

        LocalDate weekStartDate = today.minusDays(6);
        List<StudentActivityEvent> weekEvents = studentActivityEventMapper.selectList(baseQuery(studentId, courseId)
                .ge(StudentActivityEvent::getOccurredAt, weekStartDate.atStartOfDay())
                .le(StudentActivityEvent::getOccurredAt, end));
        Map<LocalDate, Long> eventCountByDate = weekEvents.stream()
                .collect(Collectors.groupingBy(event -> event.getOccurredAt().toLocalDate(), Collectors.counting()));

        List<StudentLearningReportDto.TrendItem> weeklyTrend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStartDate.plusDays(i);
            weeklyTrend.add(new StudentLearningReportDto.TrendItem(date.toString(), eventCountByDate.getOrDefault(date, 0L)));
        }

        List<StudentActivityEvent> quizEvents = studentActivityEventMapper.selectList(baseQuery(studentId, courseId)
                .eq(StudentActivityEvent::getEventType, "answer_submit"));
        long quizAnswerCount = quizEvents.size();
        long correctQuizAnswerCount = quizEvents.stream()
                .filter(this::isCorrectQuizAnswer)
                .count();
        double quizCorrectRate = quizAnswerCount == 0
                ? 0D
                : Math.round(correctQuizAnswerCount * 1000D / quizAnswerCount) / 10D;
        List<Long> weakKnowledgePointIds = quizEvents.stream()
                .filter(event -> !isCorrectQuizAnswer(event))
                .map(StudentActivityEvent::getKnowledgePointId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        return new StudentLearningReportDto(
                todayStudyMinutes,
                totalStudyMinutes,
                todayEvents.size(),
                knowledgeViewCount,
                quizAnswerCount,
                correctQuizAnswerCount,
                quizCorrectRate,
                weakKnowledgePointIds,
                weeklyTrend);
    }

    public List<StudentRecentActivityDto> getRecentActivities(Long studentId, Long courseId, int limit) {
        List<StudentActivityEvent> events = studentActivityEventMapper.selectList(baseQuery(studentId, courseId)
                .ne(StudentActivityEvent::getEventType, "page_stay")
                .orderByDesc(StudentActivityEvent::getOccurredAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
        List<StudentRecentActivityDto> recentActivities = events.stream()
                .map(toRecentActivity())
                .toList();
        List<StudentRecentActivityDto> chatActivities = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, studentId)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))))
                .stream()
                .filter(session -> session.getTitle() != null && !session.getTitle().isBlank())
                .map(session -> new StudentRecentActivityDto(
                        session.getId(),
                        courseId,
                        "ai_ask",
                        session.getTitle(),
                        "AI conversation",
                        session.getLastMessageAt() == null ? session.getUpdatedAt() : session.getLastMessageAt()))
                .toList();
        return mergeRecentActivities(recentActivities, chatActivities, limit);
    }

    private LambdaQueryWrapper<StudentActivityEvent> baseQuery(Long studentId, Long courseId) {
        LambdaQueryWrapper<StudentActivityEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentActivityEvent::getStudentId, studentId);
        if (courseId != null) {
            wrapper.eq(StudentActivityEvent::getCourseId, courseId);
        }
        return wrapper;
    }

    private Function<StudentActivityEvent, StudentRecentActivityDto> toRecentActivity() {
        return event -> new StudentRecentActivityDto(
                event.getId(),
                event.getCourseId(),
                event.getEventType(),
                titleOf(event.getEventType()),
                descriptionOf(event),
                event.getOccurredAt()
        );
    }

    private String titleOf(String eventType) {
        return switch (eventType) {
            case "page_stay" -> "Learning page visit";
            case "material_open" -> "Material opened";
            case "knowledge_view" -> "Knowledge point viewed";
            case "ai_ask" -> "AI question submitted";
            case "answer_submit" -> "Answer submitted";
            case "path_switch" -> "Learning path opened";
            default -> "Learning activity";
        };
    }

    private boolean isCorrectQuizAnswer(StudentActivityEvent event) {
        if (event.getPayloadJson() == null || event.getPayloadJson().isBlank()) {
            return false;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(event.getPayloadJson(), new TypeReference<>() {});
            return Boolean.TRUE.equals(payload.get("isCorrect"));
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private List<StudentRecentActivityDto> mergeRecentActivities(
            List<StudentRecentActivityDto> learningActivities,
            List<StudentRecentActivityDto> chatActivities,
            int limit) {
        List<StudentRecentActivityDto> merged = new ArrayList<>();
        merged.addAll(chatActivities);
        merged.addAll(learningActivities);
        return merged.stream()
                .sorted(Comparator.comparing(StudentRecentActivityDto::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();
    }

    private String descriptionOf(StudentActivityEvent event) {
        if (event.getDurationSeconds() != null && event.getDurationSeconds() > 0) {
            return titleOf(event.getEventType()) + " for " + event.getDurationSeconds() + " seconds";
        }
        return titleOf(event.getEventType());
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
