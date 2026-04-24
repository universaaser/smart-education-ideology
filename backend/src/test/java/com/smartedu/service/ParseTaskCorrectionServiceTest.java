package com.smartedu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.DocumentStructureDto;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.TeachingArtifactsDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.ParseTaskCorrection;
import com.smartedu.entity.ParseTaskIdeologyMatch;
import com.smartedu.entity.ParseTaskKnowledgePoint;
import com.smartedu.mapper.ParseTaskCorrectionMapper;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.mapper.ParseTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseTaskCorrectionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CorrectionStore correctionStore;
    private ProjectionStore projectionStore;
    private ParseTask task;
    private ParseTaskCorrectionService service;

    @BeforeEach
    void setUp() {
        task = new ParseTask();
        task.setId(1L);
        task.setUserId(9L);
        task.setCourseId(3L);
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        task.setAiAnalysis(writeJson(buildPipelineResult("Pipeline Title", "IoT Security", "Cyber Responsibility")));

        correctionStore = new CorrectionStore();
        projectionStore = new ProjectionStore();
        service = new ParseTaskCorrectionService(
                buildCorrectionMapper(correctionStore),
                buildParseTaskMapper(task),
                buildKnowledgePointMapper(projectionStore),
                buildIdeologyMatchMapper(projectionStore),
                buildAiServiceStub(task),
                new AiPipelineJsonValidator(objectMapper),
                new VectorIndexAsyncService(new VectorIndexService(objectMapper)),
                objectMapper);
        ReflectionTestUtils.setField(service, "maxKnowledgePoints", 12);
        ReflectionTestUtils.setField(service, "maxIdeologyMatchesPerPoint", 3);
        ReflectionTestUtils.setField(service, "maxQuestions", 6);
        ReflectionTestUtils.setField(service, "schemaVersion", "v1");
    }

    @Test
    void shouldPreferActiveCorrectionWhenSourceMatches() {
        ParseTaskCorrection correction = buildCorrection("ACTIVE", task.getCompletedAt(),
                buildPipelineResult("Corrected Title", "IoT Security", "Cyber Responsibility"));
        correctionStore.row = correction;

        PipelineResultDto effective = service.resolveEffectivePipelineResult(
                task,
                buildPipelineResult("Pipeline Title", "IoT Security", "Cyber Responsibility"));

        assertEquals("Corrected Title", effective.getDocumentStructure().getTitle());
    }

    @Test
    void shouldReturnPipelineBaselineWhenCorrectionIsStale() {
        correctionStore.row = buildCorrection("ACTIVE", task.getCompletedAt().minusMinutes(5),
                buildPipelineResult("Old Corrected Title", "IoT Security", "Cyber Responsibility"));

        var draft = service.getCorrectionDraft(1L);

        assertEquals("PIPELINE", draft.getSource());
        assertTrue(draft.isStale());
        assertEquals("Pipeline Title", draft.getResult().getDocumentStructure().getTitle());
    }

    @Test
    void shouldSaveCorrectionAndRefreshProjections() {
        PipelineResultDto corrected = buildPipelineResult("Corrected Title", "IoT Security", "Cyber Responsibility");

        var draft = service.saveCorrectionDraft(1L, corrected);

        assertEquals("CORRECTION", draft.getSource());
        assertEquals("ACTIVE", correctionStore.row.getStatus());
        assertEquals(1, projectionStore.knowledgePoints.size());
        assertEquals(1, projectionStore.ideologyMatches.size());
        assertEquals("IoT Security", projectionStore.knowledgePoints.get(0).getPointName());
    }

    @Test
    void shouldRejectDanglingIdeologyMatch() {
        PipelineResultDto invalid = buildPipelineResult("Corrected Title", "IoT Security", "Cyber Responsibility");
        invalid.getIdeologyMatches().get(0).setKnowledgePointName("Missing Point");

        assertThrows(RuntimeException.class, () -> service.saveCorrectionDraft(1L, invalid));
    }

    @Test
    void shouldMarkCorrectionStale() {
        correctionStore.row = buildCorrection("ACTIVE", task.getCompletedAt(),
                buildPipelineResult("Corrected Title", "IoT Security", "Cyber Responsibility"));

        service.markCorrectionStale(1L);

        assertEquals("STALE", correctionStore.row.getStatus());
    }

    private ParseTaskCorrection buildCorrection(String status, LocalDateTime sourceCompletedAt, PipelineResultDto result) {
        ParseTaskCorrection correction = new ParseTaskCorrection();
        correction.setId(1L);
        correction.setParseTaskId(1L);
        correction.setCorrectedResultJson(writeJson(result));
        correction.setSourceCompletedAt(sourceCompletedAt);
        correction.setStatus(status);
        correction.setUpdatedAt(LocalDateTime.now());
        return correction;
    }

    private PipelineResultDto buildPipelineResult(String title, String pointName, String ideologyElement) {
        DocumentStructureDto structure = new DocumentStructureDto();
        structure.setTitle(title);
        structure.setDocumentType("OUTLINE");
        structure.setOverview("Overview");
        structure.setChapterOutline(List.of("Chapter 1"));
        structure.setTeachingFocus(List.of("Focus 1"));

        KnowledgePointDto point = new KnowledgePointDto();
        point.setPointName(pointName);
        point.setDefinition("Definition");
        point.setChapter("Chapter 1");
        point.setImportance("MEDIUM");
        point.setEvidenceSnippet("Evidence");

        IdeologyMatchDto match = new IdeologyMatchDto();
        match.setKnowledgePointName(pointName);
        match.setIdeologyElement(ideologyElement);
        match.setMatchReason("Reason");
        match.setConfidence(80);

        TeachingArtifactsDto artifacts = new TeachingArtifactsDto();
        artifacts.setLectureNotes("Lecture notes");
        artifacts.setCases(List.of("Case"));
        artifacts.setQuestions(List.of(new TeachingArtifactsDto.QuestionDto(
                "Question",
                "Answer",
                List.of("Point"))));

        PipelineResultDto result = new PipelineResultDto();
        result.setDocumentStructure(structure);
        result.setKnowledgePoints(List.of(point));
        result.setIdeologyMatches(List.of(match));
        result.setTeachingArtifacts(artifacts);
        result.setWarnings(new ArrayList<>());
        result.setInferred(false);
        result.setSchemaVersion("v1");
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private ParseTaskCorrectionMapper buildCorrectionMapper(CorrectionStore store) {
        return (ParseTaskCorrectionMapper) Proxy.newProxyInstance(
                ParseTaskCorrectionMapper.class.getClassLoader(),
                new Class[]{ParseTaskCorrectionMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectOne" -> store.row;
                    case "insert" -> {
                        store.row = (ParseTaskCorrection) args[0];
                        store.row.setId(1L);
                        yield 1;
                    }
                    case "updateById" -> {
                        store.row = (ParseTaskCorrection) args[0];
                        yield 1;
                    }
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private ParseTaskMapper buildParseTaskMapper(ParseTask currentTask) {
        return (ParseTaskMapper) Proxy.newProxyInstance(
                ParseTaskMapper.class.getClassLoader(),
                new Class[]{ParseTaskMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return currentTask;
                    }
                    return primitiveDefault(method.getReturnType());
                });
    }

    private ParseTaskKnowledgePointMapper buildKnowledgePointMapper(ProjectionStore store) {
        return (ParseTaskKnowledgePointMapper) Proxy.newProxyInstance(
                ParseTaskKnowledgePointMapper.class.getClassLoader(),
                new Class[]{ParseTaskKnowledgePointMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "delete" -> {
                        store.knowledgePoints.clear();
                        yield 1;
                    }
                    case "insert" -> {
                        store.knowledgePoints.add((ParseTaskKnowledgePoint) args[0]);
                        yield 1;
                    }
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private ParseTaskIdeologyMatchMapper buildIdeologyMatchMapper(ProjectionStore store) {
        return (ParseTaskIdeologyMatchMapper) Proxy.newProxyInstance(
                ParseTaskIdeologyMatchMapper.class.getClassLoader(),
                new Class[]{ParseTaskIdeologyMatchMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "delete" -> {
                        store.ideologyMatches.clear();
                        yield 1;
                    }
                    case "insert" -> {
                        store.ideologyMatches.add((ParseTaskIdeologyMatch) args[0]);
                        yield 1;
                    }
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private AiIntelligenceService buildAiServiceStub(ParseTask currentTask) {
        return new AiIntelligenceService(
                buildParseTaskMapper(currentTask),
                objectMapper,
                null,
                new AiPipelineJsonValidator(objectMapper),
                null,
                null,
                new DocumentTextExtractor(),
                new MineruParseClient(objectMapper),
                new AiStreamBuffer(),
                buildKnowledgePointMapper(new ProjectionStore()),
                buildIdeologyMatchMapper(new ProjectionStore()),
                null
        ) {
            @Override
            public PipelineResultDto getPipelineResult(Long taskId) {
                try {
                    return objectMapper.readValue(currentTask.getAiAnalysis(), PipelineResultDto.class);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
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

    private static class CorrectionStore {
        private ParseTaskCorrection row;
    }

    private static class ProjectionStore {
        private final List<ParseTaskKnowledgePoint> knowledgePoints = new ArrayList<>();
        private final List<ParseTaskIdeologyMatch> ideologyMatches = new ArrayList<>();
    }
}
