package com.smartedu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.StudentRecentActivityDto;
import com.smartedu.entity.StudentActivityEvent;
import com.smartedu.mapper.ChatSessionMapper;
import com.smartedu.mapper.StudentActivityEventMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentActivityEventServiceTest {

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
        ChatSessionMapper chatSessionMapper = (ChatSessionMapper) Proxy.newProxyInstance(
                ChatSessionMapper.class.getClassLoader(),
                new Class<?>[]{ChatSessionMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? List.of()
                        : defaultValue(method.getReturnType()));

        StudentActivityEventService service = new StudentActivityEventService(
                eventMapper,
                chatSessionMapper,
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
