package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.ResourceCitationDto;
import com.smartedu.dto.TeachingArtifactsDto;
import com.smartedu.dto.TeachingMaterialDraftDto;
import com.smartedu.dto.TeachingMaterialSaveRequestDto;
import com.smartedu.dto.TeachingMaterialViewDto;
import com.smartedu.entity.CourseMaterialRule;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.TeachingMaterial;
import com.smartedu.mapper.CourseMaterialRuleMapper;
import com.smartedu.mapper.ParseTaskCorrectionMapper;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.TeachingMaterialMapper;
import com.smartedu.mapper.TeachingMaterialTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeachingMaterialServiceTest {

    private TeachingMaterialService teachingMaterialService;
    private TeachingMaterialStore store;

    @BeforeEach
    void setUp() {
        store = new TeachingMaterialStore();
        ParseTask parseTask = new ParseTask();
        parseTask.setId(1L);
        parseTask.setUserId(7L);
        parseTask.setCourseId(9L);
        parseTask.setFileName("iot-outline.docx");

        Map<Long, ParseTask> parseTasks = new HashMap<>();
        parseTasks.put(parseTask.getId(), parseTask);

        ParseTaskMapper parseTaskMapper = buildParseTaskMapper(parseTasks);
        TeachingMaterialMapper teachingMaterialMapper = buildTeachingMaterialMapper(store);
        SubjectKnowledgeMapper subjectKnowledgeMapper = buildSubjectKnowledgeMapper();
        TeachingMaterialTraceMapper teachingMaterialTraceMapper = buildTeachingMaterialTraceMapper();
        AiIntelligenceService aiIntelligenceService = buildAiServiceStub(buildPipelineResult());

        teachingMaterialService = new TeachingMaterialService(
                teachingMaterialMapper,
                buildCourseMaterialRuleMapper(null),
                parseTaskMapper,
                subjectKnowledgeMapper,
                aiIntelligenceService,
                buildCorrectionServiceStub(),
                teachingMaterialTraceMapper,
                new ObjectMapper()
        );
    }

    @Test
    void shouldBuildDraftFromPipelineWhenNoMaterialExists() {
        TeachingMaterialDraftDto draft = teachingMaterialService.getEditorDraft(1L);
        assertNotNull(draft);
        assertEquals(0, draft.getVersionNo());
        assertEquals("iot-outline.docx", draft.getTitle());
        assertEquals("Generated lecture notes", draft.getLectureNotes());
        assertEquals(1, draft.getCases().size());
        assertEquals(1, draft.getQuestions().size());
        assertEquals(1, draft.getTraceItems().size());
        assertEquals("IoT Governance Report", draft.getTraceItems().get(0).getResourceTitle());
        assertEquals("People Daily", draft.getTraceItems().get(0).getResourceSource());
    }

    @Test
    void shouldResolveTraceKnowledgePointIdFromBatchLookup() {
        Map<Long, ParseTask> parseTasks = new HashMap<>();
        parseTasks.put(1L, buildParseTask(1L, "iot-outline.docx"));

        TeachingMaterialService localService = new TeachingMaterialService(
                buildTeachingMaterialMapper(store),
                buildCourseMaterialRuleMapper(null),
                buildParseTaskMapper(parseTasks),
                buildSubjectKnowledgeMapper(Map.of("IoT Security", 88L)),
                buildAiServiceStub(buildPipelineResult()),
                buildCorrectionServiceStub(),
                buildTeachingMaterialTraceMapper(),
                new ObjectMapper()
        );

        TeachingMaterialDraftDto draft = localService.getEditorDraft(1L);
        assertEquals(88L, draft.getTraceItems().get(0).getKnowledgePointId());
    }

    @Test
    void shouldPromoteNewPublishedVersionAndDemoteOldLatest() {
        TeachingMaterial oldLatest = new TeachingMaterial();
        oldLatest.setId(10L);
        oldLatest.setParseTaskId(1L);
        oldLatest.setUserId(7L);
        oldLatest.setTitle("Old");
        oldLatest.setLectureNotes("Old notes");
        oldLatest.setCasesJson("[]");
        oldLatest.setQuestionsJson("[]");
        oldLatest.setVersionNo(1);
        oldLatest.setIsLatest(1);
        oldLatest.setStatus("DRAFT");
        oldLatest.setCreatedAt(LocalDateTime.now());
        oldLatest.setUpdatedAt(LocalDateTime.now());
        store.records.add(oldLatest);
        store.latest = oldLatest;
        store.idSeed.set(11L);

        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("New");
        request.setLectureNotes("Updated notes");
        request.setCases(List.of("Case A"));
        request.setQuestions(new ArrayList<>());

        TeachingMaterialViewDto saved = teachingMaterialService.savePublishedVersion(1L, request);
        assertNotNull(saved);
        assertEquals(2, saved.getVersionNo());
        assertEquals("PUBLISHED", saved.getStatus());
        assertEquals(1, saved.getIsLatest());
        assertEquals(0, oldLatest.getIsLatest());
        assertTrue(store.records.stream().anyMatch(item ->
                item.getVersionNo() == 2 && Integer.valueOf(1).equals(item.getIsLatest())));
    }

    @Test
    void shouldRejectPublishedVersionWhenQuestionIsIncomplete() {
        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("Invalid questions");
        request.setLectureNotes("Updated notes");
        request.setCases(List.of("Case A"));
        TeachingArtifactsDto.QuestionDto question = new TeachingArtifactsDto.QuestionDto();
        question.setQuestionType("SHORT_ANSWER");
        question.setDifficulty("MEDIUM");
        question.setStem("Question without answer");
        question.setReferenceAnswer(" ");
        question.setScoringPoints(List.of("Point 1"));
        request.setQuestions(List.of(question));

        assertThrows(IllegalArgumentException.class,
                () -> teachingMaterialService.savePublishedVersion(1L, request));
        assertTrue(store.records.isEmpty());
    }

    @Test
    void shouldDropIncompleteQuestionsFromDraft() {
        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("Draft with invalid questions");
        request.setLectureNotes("Draft notes");
        request.setCases(List.of("Case A"));
        TeachingArtifactsDto.QuestionDto valid = new TeachingArtifactsDto.QuestionDto();
        valid.setQuestionType("SINGLE_CHOICE");
        valid.setDifficulty("HARD");
        valid.setKnowledgePointId(88L);
        valid.setStem("Valid question");
        valid.setOptions(List.of("Option A", "Option B"));
        valid.setReferenceAnswer("Valid answer");
        valid.setScoringPoints(List.of("Point 1"));
        TeachingArtifactsDto.QuestionDto invalid = new TeachingArtifactsDto.QuestionDto();
        invalid.setStem(" ");
        invalid.setReferenceAnswer("No stem");
        request.setQuestions(List.of(valid, invalid));

        TeachingMaterialDraftDto draft = teachingMaterialService.saveDraft(1L, request);

        assertEquals(1, draft.getQuestions().size());
        assertEquals("SINGLE_CHOICE", draft.getQuestions().get(0).getQuestionType());
        assertEquals("HARD", draft.getQuestions().get(0).getDifficulty());
        assertEquals(88L, draft.getQuestions().get(0).getKnowledgePointId());
        assertEquals("Valid question", draft.getQuestions().get(0).getStem());
        assertEquals(List.of("Option A", "Option B"), draft.getQuestions().get(0).getOptions());
    }

    @Test
    void shouldRejectPublishedChoiceQuestionWithoutOptions() {
        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("Invalid choice question");
        request.setLectureNotes("Updated notes");
        request.setCases(List.of("Case A"));
        TeachingArtifactsDto.QuestionDto question = new TeachingArtifactsDto.QuestionDto();
        question.setQuestionType("SINGLE_CHOICE");
        question.setDifficulty("EASY");
        question.setStem("Choice question");
        question.setReferenceAnswer("A");
        question.setScoringPoints(List.of("Point 1"));
        request.setQuestions(List.of(question));

        assertThrows(IllegalArgumentException.class,
                () -> teachingMaterialService.savePublishedVersion(1L, request));
        assertTrue(store.records.isEmpty());
    }

    @Test
    void shouldRejectPublishedSingleChoiceWhenAnswerDoesNotMatchOptions() {
        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("Invalid single choice answer");
        request.setLectureNotes("Updated notes");
        request.setCases(List.of("Case A"));
        TeachingArtifactsDto.QuestionDto question = new TeachingArtifactsDto.QuestionDto();
        question.setQuestionType("SINGLE_CHOICE");
        question.setDifficulty("EASY");
        question.setStem("Choice question");
        question.setOptions(List.of("A. Edge device", "B. Cloud platform"));
        question.setReferenceAnswer("Sensor network");
        question.setScoringPoints(List.of("Point 1"));
        request.setQuestions(List.of(question));

        assertThrows(IllegalArgumentException.class,
                () -> teachingMaterialService.savePublishedVersion(1L, request));
        assertTrue(store.records.isEmpty());
    }

    @Test
    void shouldPublishChoiceQuestionsWhenAnswersMatchOptionLabelsOrText() {
        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("Valid choice answers");
        request.setLectureNotes("Updated notes");
        request.setCases(List.of("Case A"));
        TeachingArtifactsDto.QuestionDto single = new TeachingArtifactsDto.QuestionDto();
        single.setQuestionType("SINGLE_CHOICE");
        single.setDifficulty("EASY");
        single.setStem("Single choice");
        single.setOptions(List.of("A. Edge device", "B. Cloud platform"));
        single.setReferenceAnswer("A");
        single.setScoringPoints(List.of("Point 1"));
        TeachingArtifactsDto.QuestionDto multiple = new TeachingArtifactsDto.QuestionDto();
        multiple.setQuestionType("MULTIPLE_CHOICE");
        multiple.setDifficulty("MEDIUM");
        multiple.setStem("Multiple choice");
        multiple.setOptions(List.of("A. Sensor", "B. Gateway", "C. Workbook"));
        multiple.setReferenceAnswer("Sensor、B");
        multiple.setScoringPoints(List.of("Point 1"));
        request.setQuestions(List.of(single, multiple));

        TeachingMaterialViewDto saved = teachingMaterialService.savePublishedVersion(1L, request);

        assertEquals("PUBLISHED", saved.getStatus());
        assertEquals(2, saved.getQuestions().size());
    }

    @Test
    void shouldRejectPublishedVersionWhenCourseRulesFail() {
        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("Rule checked material");
        request.setLectureNotes("Short notes");
        request.setCases(List.of("Case without tag"));
        request.setQuestions(new ArrayList<>());

        TeachingMaterialService localService = new TeachingMaterialService(
                buildTeachingMaterialMapper(store),
                buildCourseMaterialRuleMapper("{\"minLectureCharacters\":20,\"requiredSections\":[\"Industrial IoT\"],\"requireIdeologyTagInCases\":true}"),
                buildParseTaskMapper(Map.of(1L, buildParseTask(1L, "iot-outline.docx"))),
                buildSubjectKnowledgeMapper(),
                buildAiServiceStub(buildPipelineResult()),
                buildCorrectionServiceStub(),
                buildTeachingMaterialTraceMapper(),
                new ObjectMapper()
        );

        assertThrows(IllegalArgumentException.class,
                () -> localService.savePublishedVersion(1L, request));
        assertTrue(store.records.isEmpty());
    }

    @Test
    void shouldPublishVersionWhenCourseRulesPass() {
        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("Rule checked material");
        request.setLectureNotes("Industrial IoT enables trusted device coordination and responsible data governance.");
        request.setCases(List.of("[Ideology: Responsibility] Edge device governance case"));
        request.setQuestions(new ArrayList<>());

        TeachingMaterialService localService = new TeachingMaterialService(
                buildTeachingMaterialMapper(store),
                buildCourseMaterialRuleMapper("{\"minLectureCharacters\":20,\"requiredSections\":[\"Industrial IoT\"],\"requireIdeologyTagInCases\":true}"),
                buildParseTaskMapper(Map.of(1L, buildParseTask(1L, "iot-outline.docx"))),
                buildSubjectKnowledgeMapper(),
                buildAiServiceStub(buildPipelineResult()),
                buildCorrectionServiceStub(),
                buildTeachingMaterialTraceMapper(),
                new ObjectMapper()
        );

        TeachingMaterialViewDto saved = localService.savePublishedVersion(1L, request);

        assertEquals("PUBLISHED", saved.getStatus());
        assertEquals("Rule checked material", saved.getTitle());
    }

    @Test
    void shouldCreateNewDraftWhenLatestVersionIsPublished() {
        TeachingMaterial published = new TeachingMaterial();
        published.setId(20L);
        published.setParseTaskId(1L);
        published.setUserId(7L);
        published.setTitle("Published");
        published.setLectureNotes("Published notes");
        published.setCasesJson("[]");
        published.setQuestionsJson("[]");
        published.setVersionNo(3);
        published.setIsLatest(1);
        published.setStatus("PUBLISHED");
        published.setCreatedAt(LocalDateTime.now().minusHours(1));
        published.setUpdatedAt(LocalDateTime.now().minusHours(1));
        store.records.add(published);
        store.latest = published;
        store.idSeed.set(21L);

        TeachingMaterialSaveRequestDto request = new TeachingMaterialSaveRequestDto();
        request.setTitle("Draft from published");
        request.setLectureNotes("Draft notes");
        request.setCases(List.of("Case D"));
        request.setQuestions(new ArrayList<>());

        TeachingMaterialDraftDto draft = teachingMaterialService.saveDraft(1L, request);
        assertNotNull(draft.getMaterialId());
        assertEquals("DRAFT", draft.getStatus());
        assertEquals(3, draft.getVersionNo());
        assertEquals(0, published.getIsLatest());
        assertTrue(store.records.stream().anyMatch(item ->
                !item.getId().equals(published.getId())
                        && "DRAFT".equals(item.getStatus())
                        && Integer.valueOf(1).equals(item.getIsLatest())));
    }

    @Test
    void shouldReturnVersionListInDescendingOrder() {
        TeachingMaterial v1 = new TeachingMaterial();
        v1.setId(1L);
        v1.setParseTaskId(1L);
        v1.setVersionNo(1);
        v1.setStatus("PUBLISHED");
        v1.setIsLatest(0);
        v1.setUpdatedAt(LocalDateTime.now().minusHours(1));

        TeachingMaterial v2 = new TeachingMaterial();
        v2.setId(2L);
        v2.setParseTaskId(1L);
        v2.setVersionNo(2);
        v2.setStatus("PUBLISHED");
        v2.setIsLatest(1);
        v2.setUpdatedAt(LocalDateTime.now());

        store.records.add(v1);
        store.records.add(v2);
        store.latest = v2;

        var versions = teachingMaterialService.getTaskMaterialVersions(1L);
        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersionNo());
        assertEquals(1, versions.get(1).getVersionNo());
        assertEquals(1, versions.get(0).getIsLatest());
    }

    @Test
    void shouldBuildMarkdownPreservingOriginalMarkdownAndFallbackTitle() {
        // Regression: exported content must keep "content is markdown" semantics
        // so that AI-authored Markdown (Selection Explanation, AI analysis, etc.)
        // survives export without being backslash-escaped.
        TeachingMaterial material = new TeachingMaterial();
        material.setId(100L);
        material.setParseTaskId(1L);
        material.setUserId(7L);
        material.setTitle("");
        material.setLectureNotes("Topic #1: edge *case*");
        material.setCasesJson("[\"Case [A]\"]");
        material.setQuestionsJson("[{\"questionType\":\"SINGLE_CHOICE\",\"difficulty\":\"EASY\",\"knowledgePointId\":88,\"stem\":\"Q(1) with a long stem that should stay in the Stem block instead of the heading\",\"options\":[\"A. One\",\"B. Two\"],\"referenceAnswer\":\"Ans|1\",\"scoringPoints\":[\"P-1\"]}]");
        material.setTraceJson("[{\"parseTaskId\":1,\"knowledgePointName\":\"IoT\",\"knowledgePointId\":null,\"ideologyElement\":\"Responsibility\",\"evidenceSnippet\":\"snippet\",\"matchReason\":\"reason\",\"resourceTitle\":\"People Daily Report\",\"resourceSource\":\"People Daily\",\"resourceSourceUrl\":\"https://example.com/report\",\"resourceQuotedExcerpt\":\"Quoted resource excerpt\",\"citationExplanation\":\"Resource-backed explanation\"}]");
        material.setSchemaVersion("v1");
        material.setVersionNo(3);
        material.setStatus("PUBLISHED");
        material.setIsLatest(1);
        material.setCreatedAt(LocalDateTime.now());
        material.setUpdatedAt(LocalDateTime.now());

        store.records.add(material);
        store.latest = material;

        String markdown = teachingMaterialService.exportMarkdownByMaterialId(100L);
        assertTrue(markdown.contains("# Teaching Material"));
        assertTrue(markdown.contains("## Lecture Notes"));
        assertTrue(markdown.contains("## Teaching Cases"));
        assertTrue(markdown.contains("### Case 1"));
        assertTrue(markdown.contains("## Assessment Questions"));
        assertTrue(markdown.contains("### Question 1"));
        assertTrue(markdown.contains("**Type**: SINGLE_CHOICE"));
        assertTrue(markdown.contains("**Difficulty**: EASY"));
        assertTrue(markdown.contains("**Knowledge Point ID**: 88"));
        assertTrue(markdown.contains("**Stem**"));
        assertTrue(markdown.contains("Q(1) with a long stem"));
        assertTrue(markdown.contains("**Options**"));
        assertTrue(markdown.contains("A. One"));
        assertFalse(markdown.contains("### Q(1)"));
        // 原生 Markdown 字符必须按原样保留，不再被反斜杠转义。
        assertTrue(markdown.contains("Topic #1: edge *case*"));
        assertTrue(markdown.contains("Topic #1: edge *case*"));
        assertTrue(markdown.contains("Case [A]"));
        assertTrue(markdown.contains("Ans|1"));
        assertFalse(markdown.contains("\\#"));
        assertFalse(markdown.contains("\\*"));
        assertFalse(markdown.contains("\\["));
        assertTrue(markdown.contains("## Ideology Integration"));
        assertTrue(markdown.contains("### Integration 1"));
        assertTrue(markdown.contains("**Integration Reason**"));
        assertTrue(markdown.contains("**Resource Title**"));
        assertTrue(markdown.contains("People Daily Report"));
        assertTrue(markdown.contains("**Citation Explanation**"));
        assertTrue(teachingMaterialService.buildMarkdownFileName(100L).endsWith("-v3.md"));
    }

    @Test
    void shouldGroupCourseMaterialsByParseTaskAndPreferLatestTitle() {
        ParseTask taskOne = new ParseTask();
        taskOne.setId(1L);
        taskOne.setUserId(7L);
        taskOne.setCourseId(9L);
        taskOne.setFileName("outline-a.docx");

        ParseTask taskTwo = new ParseTask();
        taskTwo.setId(2L);
        taskTwo.setUserId(7L);
        taskTwo.setCourseId(9L);
        taskTwo.setFileName("outline-b.pptx");

        Map<Long, ParseTask> parseTasks = new HashMap<>();
        parseTasks.put(1L, taskOne);
        parseTasks.put(2L, taskTwo);

        TeachingMaterialService localService = new TeachingMaterialService(
                buildTeachingMaterialMapper(store, item -> Long.valueOf(9L).equals(item.getCourseId())),
                buildCourseMaterialRuleMapper(null),
                buildParseTaskMapper(parseTasks),
                buildSubjectKnowledgeMapper(),
                buildAiServiceStub(buildPipelineResult()),
                buildCorrectionServiceStub(),
                buildTeachingMaterialTraceMapper(),
                new ObjectMapper()
        );

        TeachingMaterial latestTaskOne = new TeachingMaterial();
        latestTaskOne.setId(31L);
        latestTaskOne.setParseTaskId(1L);
        latestTaskOne.setCourseId(9L);
        latestTaskOne.setTitle("Course Outline");
        latestTaskOne.setVersionNo(2);
        latestTaskOne.setIsLatest(1);
        latestTaskOne.setStatus("PUBLISHED");
        latestTaskOne.setUpdatedAt(LocalDateTime.now());

        TeachingMaterial historyTaskOne = new TeachingMaterial();
        historyTaskOne.setId(30L);
        historyTaskOne.setParseTaskId(1L);
        historyTaskOne.setCourseId(9L);
        historyTaskOne.setTitle("");
        historyTaskOne.setVersionNo(1);
        historyTaskOne.setIsLatest(0);
        historyTaskOne.setStatus("PUBLISHED");
        historyTaskOne.setUpdatedAt(LocalDateTime.now().minusHours(2));

        TeachingMaterial latestTaskTwo = new TeachingMaterial();
        latestTaskTwo.setId(41L);
        latestTaskTwo.setParseTaskId(2L);
        latestTaskTwo.setCourseId(9L);
        latestTaskTwo.setTitle("");
        latestTaskTwo.setVersionNo(1);
        latestTaskTwo.setIsLatest(1);
        latestTaskTwo.setStatus("DRAFT");
        latestTaskTwo.setUpdatedAt(LocalDateTime.now().minusHours(1));

        TeachingMaterial otherCourse = new TeachingMaterial();
        otherCourse.setId(51L);
        otherCourse.setParseTaskId(3L);
        otherCourse.setCourseId(11L);
        otherCourse.setTitle("Other Course");
        otherCourse.setVersionNo(1);
        otherCourse.setIsLatest(1);
        otherCourse.setStatus("PUBLISHED");
        otherCourse.setUpdatedAt(LocalDateTime.now());

        store.records.add(historyTaskOne);
        store.records.add(latestTaskOne);
        store.records.add(latestTaskTwo);
        store.records.add(otherCourse);

        var groups = localService.getCourseMaterialGroups(9L);
        assertEquals(2, groups.size());
        assertEquals(1L, groups.get(0).getParseTaskId());
        assertEquals("Course Outline", groups.get(0).getDisplayTitle());
        assertEquals(2, groups.get(0).getVersions().size());
        assertEquals(2, groups.get(0).getVersions().get(0).getVersionNo());
        assertEquals("outline-b.pptx", groups.get(1).getDisplayTitle());
        assertEquals(1, groups.get(1).getLatestVersionNo().intValue());
        assertTrue(groups.stream().noneMatch(group -> group.getParseTaskId().equals(3L)));
    }

    private ParseTaskMapper buildParseTaskMapper(Map<Long, ParseTask> tasks) {
        return (ParseTaskMapper) Proxy.newProxyInstance(
                ParseTaskMapper.class.getClassLoader(),
                new Class[]{ParseTaskMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return tasks.get(args[0]);
                    }
                    if ("selectBatchIds".equals(method.getName()) && args[0] instanceof List<?> ids) {
                        return ids.stream()
                                .map(tasks::get)
                                .filter(item -> item != null)
                                .toList();
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }

    private TeachingMaterialMapper buildTeachingMaterialMapper(TeachingMaterialStore localStore) {
        return buildTeachingMaterialMapper(localStore, item -> true);
    }

    private CourseMaterialRuleMapper buildCourseMaterialRuleMapper(String ruleJson) {
        return (CourseMaterialRuleMapper) Proxy.newProxyInstance(
                CourseMaterialRuleMapper.class.getClassLoader(),
                new Class[]{CourseMaterialRuleMapper.class},
                (proxy, method, args) -> {
                    if ("selectOne".equals(method.getName()) && ruleJson != null) {
                        CourseMaterialRule rule = new CourseMaterialRule();
                        rule.setId(1L);
                        rule.setCourseId(9L);
                        rule.setRuleJson(ruleJson);
                        rule.setUpdatedAt(LocalDateTime.now());
                        return rule;
                    }
                    return primitiveDefault(method.getReturnType());
                });
    }

    private TeachingMaterialMapper buildTeachingMaterialMapper(
            TeachingMaterialStore localStore,
            Predicate<TeachingMaterial> filter) {
        return (TeachingMaterialMapper) Proxy.newProxyInstance(
                TeachingMaterialMapper.class.getClassLoader(),
                new Class[]{TeachingMaterialMapper.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("selectOne".equals(methodName) && args[0] instanceof LambdaQueryWrapper) {
                        return localStore.latest != null && filter.test(localStore.latest)
                                ? localStore.latest
                                : null;
                    }
                    if ("selectList".equals(methodName) && args[0] instanceof LambdaQueryWrapper) {
                        return localStore.records.stream()
                                .filter(filter)
                                .sorted((a, b) -> Integer.compare(
                                        b.getVersionNo() == null ? 0 : b.getVersionNo(),
                                        a.getVersionNo() == null ? 0 : a.getVersionNo()))
                                .toList();
                    }
                    if ("insert".equals(methodName) && args[0] instanceof TeachingMaterial material) {
                        material.setId(localStore.idSeed.getAndIncrement());
                        localStore.records.add(material);
                        if (Integer.valueOf(1).equals(material.getIsLatest())) {
                            localStore.latest = material;
                        }
                        return 1;
                    }
                    if ("updateById".equals(methodName) && args[0] instanceof TeachingMaterial material) {
                        for (int i = 0; i < localStore.records.size(); i++) {
                            TeachingMaterial existing = localStore.records.get(i);
                            if (existing.getId().equals(material.getId())) {
                                localStore.records.set(i, material);
                                break;
                            }
                        }
                        if (Integer.valueOf(1).equals(material.getIsLatest())) {
                            localStore.latest = material;
                        } else if (localStore.latest != null && localStore.latest.getId().equals(material.getId())) {
                            localStore.latest = null;
                        }
                        return 1;
                    }
                    if ("selectById".equals(methodName) && args[0] instanceof Long id) {
                        return localStore.records.stream()
                                .filter(item -> item.getId().equals(id))
                                .findFirst()
                                .orElse(null);
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }

    private SubjectKnowledgeMapper buildSubjectKnowledgeMapper() {
        return buildSubjectKnowledgeMapper(Map.of());
    }

    private SubjectKnowledgeMapper buildSubjectKnowledgeMapper(Map<String, Long> subjectKnowledgeIdMap) {
        return (SubjectKnowledgeMapper) Proxy.newProxyInstance(
                SubjectKnowledgeMapper.class.getClassLoader(),
                new Class[]{SubjectKnowledgeMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        List<SubjectKnowledge> results = new ArrayList<>();
                        for (Map.Entry<String, Long> entry : subjectKnowledgeIdMap.entrySet()) {
                            SubjectKnowledge subjectKnowledge = new SubjectKnowledge();
                            subjectKnowledge.setId(entry.getValue());
                            subjectKnowledge.setName(entry.getKey());
                            results.add(subjectKnowledge);
                        }
                        return results;
                    }
                    return null;
                }
        );
    }

    private AiIntelligenceService buildAiServiceStub(PipelineResultDto resultDto) {
        return new AiIntelligenceService(
                (ParseTaskMapper) Proxy.newProxyInstance(
                        ParseTaskMapper.class.getClassLoader(),
                        new Class[]{ParseTaskMapper.class},
                        (proxy, method, args) -> null),
                new ObjectMapper(),
                null,
                new AiPipelineJsonValidator(new ObjectMapper()),
                null,
                null,
                new DocumentTextExtractor(),
                new MineruParseClient(new ObjectMapper()),
                new AiStreamBuffer(),
                emptyKnowledgePointMapper(),
                emptyIdeologyMatchMapper(),
                null
        ) {
            @Override
            public PipelineResultDto getPipelineResult(Long taskId) {
                return resultDto;
            }
        };
    }

    private ParseTaskCorrectionService buildCorrectionServiceStub() {
        return new ParseTaskCorrectionService(
                (ParseTaskCorrectionMapper) Proxy.newProxyInstance(
                        ParseTaskCorrectionMapper.class.getClassLoader(),
                        new Class[]{ParseTaskCorrectionMapper.class},
                        (proxy, method, args) -> null),
                (ParseTaskMapper) Proxy.newProxyInstance(
                        ParseTaskMapper.class.getClassLoader(),
                        new Class[]{ParseTaskMapper.class},
                        (proxy, method, args) -> null),
                emptyKnowledgePointMapper(),
                emptyIdeologyMatchMapper(),
                buildAiServiceStub(buildPipelineResult()),
                new AiPipelineJsonValidator(new ObjectMapper()),
                new VectorIndexAsyncService(new VectorIndexService(new ObjectMapper())),
                new ObjectMapper()
        ) {
            @Override
            public PipelineResultDto resolveEffectivePipelineResult(ParseTask task, PipelineResultDto fallback) {
                return fallback;
            }
        };
    }

    private ParseTaskKnowledgePointMapper emptyKnowledgePointMapper() {
        return (ParseTaskKnowledgePointMapper) Proxy.newProxyInstance(
                ParseTaskKnowledgePointMapper.class.getClassLoader(),
                new Class[]{ParseTaskKnowledgePointMapper.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
    }

    private ParseTaskIdeologyMatchMapper emptyIdeologyMatchMapper() {
        return (ParseTaskIdeologyMatchMapper) Proxy.newProxyInstance(
                ParseTaskIdeologyMatchMapper.class.getClassLoader(),
                new Class[]{ParseTaskIdeologyMatchMapper.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
    }

    private Object primitiveDefault(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.isPrimitive()) {
            return 0;
        }
        return null;
    }

    private TeachingMaterialTraceMapper buildTeachingMaterialTraceMapper() {
        return (TeachingMaterialTraceMapper) Proxy.newProxyInstance(
                TeachingMaterialTraceMapper.class.getClassLoader(),
                new Class[]{TeachingMaterialTraceMapper.class},
                (proxy, method, args) -> {
                    if ("selectCount".equals(method.getName())) {
                        return 0L;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }

    private PipelineResultDto buildPipelineResult() {
        PipelineResultDto resultDto = new PipelineResultDto();

        TeachingArtifactsDto artifacts = new TeachingArtifactsDto();
        artifacts.setLectureNotes("Generated lecture notes");
        artifacts.setCases(List.of("Case 1"));
        TeachingArtifactsDto.QuestionDto question = new TeachingArtifactsDto.QuestionDto();
        question.setQuestionType("SHORT_ANSWER");
        question.setDifficulty("MEDIUM");
        question.setStem("Question 1");
        question.setReferenceAnswer("Answer 1");
        question.setScoringPoints(List.of("Point 1"));
        artifacts.setQuestions(List.of(question));
        resultDto.setTeachingArtifacts(artifacts);

        KnowledgePointDto point = new KnowledgePointDto();
        point.setPointName("IoT Security");
        point.setEvidenceSnippet("Evidence snippet");
        point.setResourceCitations(List.of(
                new ResourceCitationDto(
                        "R1",
                        101L,
                        "IoT Governance Report",
                        "People Daily",
                        "https://example.com/iot-governance",
                        "Industrial IoT systems need clear safety and governance boundaries.",
                        "This excerpt helps explain the governance implication of the knowledge point.")));
        resultDto.setKnowledgePoints(List.of(point));

        IdeologyMatchDto match = new IdeologyMatchDto();
        match.setKnowledgePointName("IoT Security");
        match.setIdeologyElement("Cyber Responsibility");
        match.setMatchReason("Aligned with public safety and ethics");
        match.setCitationExplanation("The cited resource connects IoT security with public responsibility and governance.");
        match.setResourceCitations(point.getResourceCitations());
        resultDto.setIdeologyMatches(List.of(match));

        resultDto.setSchemaVersion("v1");
        return resultDto;
    }

    private ParseTask buildParseTask(Long id, String fileName) {
        ParseTask parseTask = new ParseTask();
        parseTask.setId(id);
        parseTask.setUserId(7L);
        parseTask.setCourseId(9L);
        parseTask.setFileName(fileName);
        return parseTask;
    }

    private static class TeachingMaterialStore {
        private final List<TeachingMaterial> records = new ArrayList<>();
        private final AtomicLong idSeed = new AtomicLong(1L);
        private TeachingMaterial latest;
    }
}
