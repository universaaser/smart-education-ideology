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
import java.time.LocalDate;
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
                    if ("selectMaps".equals(method.getName())) {
                        sqlSegment.set(readSqlSegment(args[0]));
                        return List.of();
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

    @Test
    void shouldPushActivityTrendAggregationDownToDatabase() {
        initTables();
        AtomicReference<String> eventSql = new AtomicReference<>("");
        LocalDate today = LocalDate.now();
        // 伪造「数据库聚合后」返回的两行:今天 3 条、前天 5 条
        List<Map<String, Object>> dbRows = List.of(
                Map.<String, Object>of("day", today.toString(), "value", 3L),
                Map.<String, Object>of("day", today.minusDays(2).toString(), "value", 5L));

        StudentActivityEventMapper eventMapper = (StudentActivityEventMapper) Proxy.newProxyInstance(
                StudentActivityEventMapper.class.getClassLoader(),
                new Class<?>[]{StudentActivityEventMapper.class},
                (proxy, method, args) -> {
                    if ("selectMaps".equals(method.getName())) {
                        eventSql.set(readSqlSegment(args[0]));
                        return dbRows;
                    }
                    if ("selectList".equals(method.getName())) {
                        throw new AssertionError("不应再全量读取事件表(selectList),聚合应下推数据库");
                    }
                    return defaultValue(method.getReturnType());
                });

        DashboardService service = new DashboardService(
                mapper(CourseMapper.class),
                mapper(SubjectKnowledgeMapper.class),
                mapper(SubjectIdeologyMatchMapper.class),
                mapper(StudentActivityMapper.class),
                mapper(SystemActivityMapper.class),
                mapper(StudentAlertRecordMapper.class),
                mapper(ResourceMapper.class),
                mapper(ParseTaskMapper.class, List.of(), 0L, new AtomicReference<>("")),
                mapper(TeachingMaterialMapper.class),
                eventMapper);

        Map<String, Object> overview = service.getOverview(8L);

        assertTrue(eventSql.get().contains("group by"), "SQL 应包含 GROUP BY,聚合在数据库完成");
        List<?> trend = (List<?>) overview.get("activityTrend");
        assertEquals(7, trend.size(), "返回结构不变:仍为最近 7 天");
        Map<?, ?> todayPoint = (Map<?, ?>) trend.get(6);
        assertEquals(today.toString(), todayPoint.get("day"));
        assertEquals(3L, todayPoint.get("value"), "今天的计数应来自数据库聚合结果");
    }
}
