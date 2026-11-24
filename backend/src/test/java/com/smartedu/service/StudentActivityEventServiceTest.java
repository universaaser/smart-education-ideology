package com.smartedu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.StudentLearningReportDto;
import com.smartedu.dto.StudentRecentActivityDto;
import com.smartedu.entity.StudentActivityEvent;
import com.smartedu.mapper.ChatSessionMapper;
import com.smartedu.mapper.StudentActivityEventMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentActivityEventServiceTest {

    @Test
    void shouldUseDatabaseSumAndKeepOtherReportMetrics() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StudentActivityEvent.class);
        LocalDate today = LocalDate.now();
        List<StudentActivityEvent> todayEvents = List.of(
                buildEvent(1L, "material_open", null, 120, null, today.atTime(9, 0)),
                buildEvent(2L, "knowledge_view", 12L, 60, null, today.atTime(10, 0)),
                buildEvent(3L, "answer_submit", 13L, 30, "{\"isCorrect\":true}", today.atTime(11, 0)));
        StudentActivityEvent previousEvent = buildEvent(
                4L, "material_open", null, 180, null, today.minusDays(1).atTime(9, 0));
        StudentActivityEvent wrongAnswer = buildEvent(
                5L, "answer_submit", 99L, 210, "{\"isCorrect\":false}", today.minusDays(2).atTime(9, 0));
        AtomicInteger selectListCalls = new AtomicInteger();
        AtomicBoolean databaseSumCalled = new AtomicBoolean(false);

        StudentActivityEventMapper eventMapper = (StudentActivityEventMapper) Proxy.newProxyInstance(
                StudentActivityEventMapper.class.getClassLoader(),
                new Class<?>[]{StudentActivityEventMapper.class},
                (proxy, method, args) -> {
                    if ("sumDurationSeconds".equals(method.getName())) {
                        databaseSumCalled.set(true);
                        assertEquals(9L, args[0]);
                        assertEquals(3L, args[1]);
                        return 600L;
                    }
                    if ("selectList".equals(method.getName())) {
                        return switch (selectListCalls.getAndIncrement()) {
                            case 0 -> todayEvents;
                            case 1 -> List.of(todayEvents.get(0), todayEvents.get(1), todayEvents.get(2), previousEvent, wrongAnswer);
                            case 2 -> List.of(todayEvents.get(2), wrongAnswer);
                            default -> throw new AssertionError("Unexpected full-table event query");
                        };
                    }
                    return defaultValue(method.getReturnType());
                });
        StudentActivityEventService service = new StudentActivityEventService(
                eventMapper,
                emptyChatSessionMapper(),
                new ObjectMapper());

        StudentLearningReportDto report = service.getReport(9L, 3L);

        assertTrue(databaseSumCalled.get());
        assertEquals(3, selectListCalls.get());
        assertEquals(3, report.getTodayStudyMinutes());
        assertEquals(10, report.getTotalStudyMinutes());
        assertEquals(3, report.getTodayEventCount());
        assertEquals(1, report.getKnowledgeViewCount());
        assertEquals(2, report.getQuizAnswerCount());
        assertEquals(1, report.getCorrectQuizAnswerCount());
        assertEquals(50D, report.getQuizCorrectRate());
        assertEquals(List.of(99L), report.getWeakKnowledgePointIds());
        assertEquals(7, report.getWeeklyTrend().size());
        assertEquals(1L, report.getWeeklyTrend().get(4).getEventCount());
        assertEquals(1L, report.getWeeklyTrend().get(5).getEventCount());
        assertEquals(3L, report.getWeeklyTrend().get(6).getEventCount());
    }

    @Test
    void shouldExcludePageStayBeforeRecentActivityLimit() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StudentActivityEvent.class);
        AtomicBoolean queryExcludedPageStay = new AtomicBoolean(false);
        StudentActivityEventMapper eventMapper = (StudentActivityEventMapper) Proxy.newProxyInstance(
                StudentActivityEventMapper.class.getClassLoader(),
                new Class<?>[]{StudentActivityEventMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        String sqlSegment = readSqlSegment(args[0]);
                        queryExcludedPageStay.set(sqlSegment.contains("event_type")
                                && (sqlSegment.contains("<>") || sqlSegment.toLowerCase().contains(" not ")));
                        return List.of(buildEvent("material_open"));
                    }
                    return defaultValue(method.getReturnType());
                });
        StudentActivityEventService service = new StudentActivityEventService(
                eventMapper,
                emptyChatSessionMapper(),
                new ObjectMapper());

        List<StudentRecentActivityDto> result = service.getRecentActivities(9L, 3L, 6);

        assertTrue(queryExcludedPageStay.get());
        assertEquals(1, result.size());
        assertEquals("material_open", result.get(0).getEventType());
    }

    private static StudentActivityEvent buildEvent(String eventType) {
        StudentActivityEvent event = new StudentActivityEvent();
        event.setId(1L);
        event.setStudentId(9L);
        event.setCourseId(3L);
        event.setEventType(eventType);
        event.setOccurredAt(LocalDateTime.now());
        return event;
    }

    private static StudentActivityEvent buildEvent(
            Long id,
            String eventType,
            Long knowledgePointId,
            Integer durationSeconds,
            String payloadJson,
            LocalDateTime occurredAt) {
        StudentActivityEvent event = buildEvent(eventType);
        event.setId(id);
        event.setKnowledgePointId(knowledgePointId);
        event.setDurationSeconds(durationSeconds);
        event.setPayloadJson(payloadJson);
        event.setOccurredAt(occurredAt);
        return event;
    }

    private static ChatSessionMapper emptyChatSessionMapper() {
        return (ChatSessionMapper) Proxy.newProxyInstance(
                ChatSessionMapper.class.getClassLoader(),
                new Class<?>[]{ChatSessionMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? List.of()
                        : defaultValue(method.getReturnType()));
    }

    private static String readSqlSegment(Object wrapper) throws Exception {
        Object sqlSegment = wrapper.getClass().getMethod("getSqlSegment").invoke(wrapper);
        return sqlSegment == null ? "" : sqlSegment.toString().toLowerCase();
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class || returnType == long.class || returnType == short.class || returnType == byte.class) {
            return 0;
        }
        if (returnType == float.class || returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
