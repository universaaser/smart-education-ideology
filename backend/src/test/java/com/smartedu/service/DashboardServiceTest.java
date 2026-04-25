package com.smartedu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.core.toolkit.support.SerializedLambda;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.Resource;
import com.smartedu.entity.StudentActivityEvent;
import com.smartedu.entity.StudentAlertRecord;
import com.smartedu.entity.SubjectIdeologyMatch;
import com.smartedu.entity.TeachingMaterial;
import com.smartedu.mapper.CourseMapper;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.ResourceMapper;
import com.smartedu.mapper.StudentActivityEventMapper;
import com.smartedu.mapper.StudentActivityMapper;
import com.smartedu.mapper.StudentAlertRecordMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.SystemActivityMapper;
import com.smartedu.mapper.TeachingMaterialMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceTest {

    @Test
    void shouldBuildOverviewFromExistingModules() {
        initTables();
        AtomicReference<String> alertSql = new AtomicReference<>("");
        AtomicReference<String> resourceSql = new AtomicReference<>("");
        AtomicReference<String> matchSql = new AtomicReference<>("");

        DashboardService service = new DashboardService(
                mapper(CourseMapper.class),
                mapper(SubjectKnowledgeMapper.class),
                mapper(SubjectIdeologyMatchMapper.class, List.of(), 4L, matchSql),
                mapper(StudentActivityMapper.class),
                mapper(SystemActivityMapper.class),
                mapper(StudentAlertRecordMapper.class, List.of(), 2L, alertSql),
                mapper(ResourceMapper.class, List.of(), 3L, resourceSql),
                mapper(ParseTaskMapper.class, List.of(parseTask()), 1L, new AtomicReference<>("")),
                mapper(TeachingMaterialMapper.class, List.of(), 5L, new AtomicReference<>("")),
                mapper(StudentActivityEventMapper.class, List.of(activityEvent()), 1L, new AtomicReference<>("")));

        Map<String, Object> overview = service.getOverview(8L);

        List<?> cards = (List<?>) overview.get("todoCards");
        assertEquals(4, cards.size());
        assertEquals(2L, ((Map<?, ?>) cards.get(0)).get("count"));
        assertTrue(alertSql.get().contains("status") && alertSql.get().contains("<>"));
        assertTrue(resourceSql.get().contains("review_status"));
        assertTrue(matchSql.get().contains("review_status"));
        assertEquals(1, ((List<?>) overview.get("recentParseTasks")).size());
        assertEquals(7, ((List<?>) overview.get("activityTrend")).size());
    }

    private static void initTables() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, StudentAlertRecord.class);
        TableInfoHelper.initTableInfo(assistant, Resource.class);
        TableInfoHelper.initTableInfo(assistant, SubjectIdeologyMatch.class);
        TableInfoHelper.initTableInfo(assistant, ParseTask.class);
        TableInfoHelper.initTableInfo(assistant, TeachingMaterial.class);
        TableInfoHelper.initTableInfo(assistant, StudentActivityEvent.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> mapperType) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> mapperType, List<?> selectList, Long selectCount, AtomicReference<String> sqlSegment) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> {
                    if ("selectCount".equals(method.getName())) {
                        sqlSegment.set(readSqlSegment(args[0]));
                        return selectCount;
                    }
                    if ("selectList".equals(method.getName())) {
                        sqlSegment.set(readSqlSegment(args[0]));
                        return selectList;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ParseTask parseTask() {
        ParseTask task = new ParseTask();
        task.setId(1L);
        task.setFileName("chapter.pdf");
        task.setStatus("COMPLETED");
        task.setProgress(100);
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private static StudentActivityEvent activityEvent() {
        StudentActivityEvent event = new StudentActivityEvent();
        event.setId(1L);
        event.setOccurredAt(LocalDateTime.now());
        return event;
    }

    private static String readSqlSegment(Object wrapper) throws Exception {
        if (wrapper == null) {
            return "";
        }
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
