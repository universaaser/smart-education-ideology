package com.smartedu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartedu.dto.DocumentStructureDto;
import com.smartedu.dto.ParseTaskCorrectionDraftDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.TeachingMaterial;
import com.smartedu.mapper.ParseTaskCorrectionMapper;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.mapper.CourseMaterialRuleMapper;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.TeachingMaterialMapper;
import com.smartedu.mapper.TeachingMaterialTraceMapper;
import com.smartedu.service.AiPipelineJsonValidator;
import com.smartedu.service.AiIntelligenceService;
import com.smartedu.service.AiStreamBuffer;
import com.smartedu.service.DocumentTextExtractor;
import com.smartedu.service.ParseTaskCorrectionService;
import com.smartedu.service.TeachingMaterialService;
import com.smartedu.service.VectorIndexAsyncService;
import com.smartedu.service.VectorIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerTest {

    private MockMvc mockMvc;
    private PipelineResultDto resultDto;
    private AiStreamBuffer aiStreamBuffer;

    @BeforeEach
    void setUp() throws Exception {
        ParseTask task = new ParseTask();
        task.setId(1L);
        task.setUserId(9L);
        task.setFileName("iot-outline.docx");
        task.setStatus("COMPLETED");
        task.setProgress(100);
        task.setCurrentStep("Completed");
        task.setCompletedAt(LocalDateTime.now());

        DocumentStructureDto storedStructure = new DocumentStructureDto();
        storedStructure.setTitle("IoT Teaching Outline");
        storedStructure.setParseMode("MINERU");
        storedStructure.setRawMarkdown("# IoT Teaching Outline\n\nMarkdown body");
        PipelineResultDto storedPipeline = new PipelineResultDto();
        storedPipeline.setDocumentStructure(storedStructure);
        storedPipeline.setSchemaVersion("v1");
        task.setParsedContent(new ObjectMapper().writeValueAsString(storedStructure));
        task.setAiAnalysis(new ObjectMapper().writeValueAsString(storedPipeline));

        ParseTaskMapper parseTaskMapper = buildMapperStub(task);
        aiStreamBuffer = new AiStreamBuffer();
        aiStreamBuffer.beginTask(1L);
        aiStreamBuffer.appendChunk("live output");
        aiStreamBuffer.endTask();

        resultDto = new PipelineResultDto();
        DocumentStructureDto structureDto = new DocumentStructureDto();
        structureDto.setTitle("IoT Teaching Outline");
        resultDto.setDocumentStructure(structureDto);
        resultDto.setWarnings(new ArrayList<>());
        resultDto.setSchemaVersion("v1");

        AiIntelligenceService aiIntelligenceService = new StubAiService(resultDto, task);
        ParseTaskCorrectionService parseTaskCorrectionService = new StubCorrectionService(resultDto);
        TeachingMaterialService teachingMaterialService = new TeachingMaterialService(
                (TeachingMaterialMapper) Proxy.newProxyInstance(
                        TeachingMaterialMapper.class.getClassLoader(),
                        new Class[]{TeachingMaterialMapper.class},
                        (proxy, method, args) -> {
                            if ("selectList".equals(method.getName())) {
                                return new ArrayList<>();
                            }
                            if ("insert".equals(method.getName()) && args[0] instanceof TeachingMaterial material) {
                                material.setId(1L);
                                return 1;
                            }
                            if ("updateById".equals(method.getName())) {
                                return 1;
                            }
                            if (method.getReturnType().equals(boolean.class)) {
                                return false;
                            }
                            if (method.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                        }
                ),
                (CourseMaterialRuleMapper) Proxy.newProxyInstance(
                        CourseMaterialRuleMapper.class.getClassLoader(),
                        new Class[]{CourseMaterialRuleMapper.class},
                        (proxy, method, args) -> null
                ),
                parseTaskMapper,
                (SubjectKnowledgeMapper) Proxy.newProxyInstance(
                        SubjectKnowledgeMapper.class.getClassLoader(),
                        new Class[]{SubjectKnowledgeMapper.class},
                        (proxy, method, args) -> null
                ),
                aiIntelligenceService,
                parseTaskCorrectionService,
                (TeachingMaterialTraceMapper) Proxy.newProxyInstance(
                        TeachingMaterialTraceMapper.class.getClassLoader(),
                        new Class[]{TeachingMaterialTraceMapper.class},
                        (proxy, method, args) -> {
                            if ("selectCount".equals(method.getName())) {
                                return 0L;
                            }
                            if ("insert".equals(method.getName()) || "delete".equals(method.getName())) {
                                return 1;
                            }
                            if (method.getReturnType().equals(boolean.class)) {
                                return false;
                            }
                            if (method.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                        }
                ),
                new ObjectMapper()
        );
        UploadController controller = new UploadController(
                parseTaskMapper,
                aiIntelligenceService,
                teachingMaterialService,
                parseTaskCorrectionService,
                aiStreamBuffer);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnResultDetailWhenTaskExists() throws Exception {
        mockMvc.perform(get("/api/upload/tasks/1/result-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documentStructure.title").value("IoT Teaching Outline"));
    }

    @Test
    void shouldRegenerateWhenTaskExists() throws Exception {
        mockMvc.perform(post("/api/upload/tasks/1/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.schemaVersion").value("v1"));
    }

    @Test
    void shouldReturnEditorDraftWhenTaskExists() throws Exception {
        mockMvc.perform(get("/api/upload/tasks/1/editor-draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.parseTaskId").value(1));
    }

    @Test
    void shouldReturnMaterialVersionsWhenTaskExists() throws Exception {
        mockMvc.perform(get("/api/upload/tasks/1/materials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void shouldSaveEditorDraftWhenBodyIsEmpty() throws Exception {
        mockMvc.perform(put("/api/upload/tasks/1/editor-draft")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("iot-outline.docx"));
    }

    @Test
    void shouldSavePublishedMaterialWhenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/upload/tasks/1/materials")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("iot-outline.docx"));
    }

    @Test
    void shouldReturnBadRequestWhenPublishedMaterialValidationFails() throws Exception {
        String body = """
                {
                  "title": "Invalid",
                  "questions": [
                    {
                      "questionType": "SINGLE_CHOICE",
                      "stem": "Choice question",
                      "referenceAnswer": "A",
                      "scoringPoints": ["Point 1"]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/upload/tasks/1/materials")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Choice assessment questions require options"));
    }

    @Test
    void shouldReturnCorrectionDraftWhenTaskExists() throws Exception {
        mockMvc.perform(get("/api/upload/tasks/1/correction-draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.source").value("PIPELINE"))
                .andExpect(jsonPath("$.data.result.documentStructure.title").value("IoT Teaching Outline"));
    }

    @Test
    void shouldSaveCorrectionDraftWhenTaskExists() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/upload/tasks/1/correction-draft")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(resultDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.source").value("CORRECTION"));
    }

    @Test
    void shouldStartReparseWhenTaskIsCompleted() throws Exception {
        mockMvc.perform(post("/api/upload/tasks/1/reparse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(1));
    }

    @Test
    void shouldRejectUploadWithoutUserId() throws Exception {
        mockMvc.perform(multipart("/api/upload/file")
                        .file("file", "demo".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("User id cannot be empty"));
    }

    @Test
    void shouldReturnMarkdownContentFromStoredParsedContentJson() throws Exception {
        mockMvc.perform(get("/api/upload/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.parsedContent").value("# IoT Teaching Outline\n\nMarkdown body"));
    }

    @Test
    void shouldReturnLiveLogSliceWhenTaskExists() throws Exception {
        mockMvc.perform(get("/api/upload/tasks/1/live-log").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isString())
                .andExpect(jsonPath("$.data.cursor").isNumber())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnNotFoundForLiveLogWhenTaskDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/upload/tasks/2/live-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void shouldReturnTaskHistoryPageWhenUserIdProvided() throws Exception {
        mockMvc.perform(get("/api/upload/tasks")
                        .param("userId", "9")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].taskId").value(1))
                .andExpect(jsonPath("$.data.records[0].fileName").value("iot-outline.docx"))
                .andExpect(jsonPath("$.data.records[0].parseMode").value("MINERU"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    private ParseTaskMapper buildMapperStub(ParseTask task) {
        return (ParseTaskMapper) Proxy.newProxyInstance(
                ParseTaskMapper.class.getClassLoader(),
                new Class[]{ParseTaskMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return Long.valueOf(1L).equals(args[0]) ? task : null;
                    }
                    if ("selectPage".equals(method.getName()) && args[0] instanceof Page<?> page) {
                        @SuppressWarnings("unchecked")
                        Page<ParseTask> typedPage = (Page<ParseTask>) page;
                        typedPage.setRecords(List.of(task));
                        typedPage.setTotal(1L);
                        return typedPage;
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

    private static class StubAiService extends AiIntelligenceService {

        private final PipelineResultDto pipelineResultDto;

        private final ParseTask task;

        StubAiService(PipelineResultDto pipelineResultDto, ParseTask task) {
            super(
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
                    new com.smartedu.service.MineruParseClient(new ObjectMapper()),
                    new AiStreamBuffer(),
                    emptyKnowledgePointMapper(),
                    emptyIdeologyMatchMapper(),
                    null
            );
            this.pipelineResultDto = pipelineResultDto;
            this.task = task;
        }

        @Override
        public PipelineResultDto getPipelineResult(Long taskId) {
            return pipelineResultDto;
        }

        @Override
        public PipelineResultDto regenerateTask(Long taskId) {
            return pipelineResultDto;
        }

        @Override
        public void processDocumentAsync(Long taskId) {
            // no-op for controller tests
        }

        @Override
        public ParseTask reparseTask(Long taskId) {
            task.setStatus("UPLOADING");
            task.setProgress(10);
            task.setCurrentStep("Reparse requested");
            return task;
        }

        @Override
        public ParseTask retryTask(Long taskId) {
            task.setStatus("UPLOADING");
            task.setProgress(10);
            task.setCurrentStep("Retry requested");
            return task;
        }

        private static ParseTaskKnowledgePointMapper emptyKnowledgePointMapper() {
            return (ParseTaskKnowledgePointMapper) Proxy.newProxyInstance(
                    ParseTaskKnowledgePointMapper.class.getClassLoader(),
                    new Class[]{ParseTaskKnowledgePointMapper.class},
                    (proxy, method, args) -> primitiveDefault(method.getReturnType()));
        }

        private static ParseTaskIdeologyMatchMapper emptyIdeologyMatchMapper() {
            return (ParseTaskIdeologyMatchMapper) Proxy.newProxyInstance(
                    ParseTaskIdeologyMatchMapper.class.getClassLoader(),
                    new Class[]{ParseTaskIdeologyMatchMapper.class},
                    (proxy, method, args) -> primitiveDefault(method.getReturnType()));
        }

        private static Object primitiveDefault(Class<?> returnType) {
            if (returnType.equals(boolean.class)) {
                return false;
            }
            if (returnType.isPrimitive()) {
                return 0;
            }
            return null;
        }
    }

    private static class StubCorrectionService extends ParseTaskCorrectionService {

        private final PipelineResultDto resultDto;

        StubCorrectionService(PipelineResultDto resultDto) {
            super(
                    (ParseTaskCorrectionMapper) Proxy.newProxyInstance(
                            ParseTaskCorrectionMapper.class.getClassLoader(),
                            new Class[]{ParseTaskCorrectionMapper.class},
                            (proxy, method, args) -> null),
                    (ParseTaskMapper) Proxy.newProxyInstance(
                            ParseTaskMapper.class.getClassLoader(),
                            new Class[]{ParseTaskMapper.class},
                            (proxy, method, args) -> null),
                    StubAiService.emptyKnowledgePointMapper(),
                    StubAiService.emptyIdeologyMatchMapper(),
                    null,
                    new AiPipelineJsonValidator(new ObjectMapper()),
                    new VectorIndexAsyncService(new VectorIndexService(new ObjectMapper())),
                    new ObjectMapper());
            this.resultDto = resultDto;
        }

        @Override
        public ParseTaskCorrectionDraftDto getCorrectionDraft(Long taskId) {
            ParseTaskCorrectionDraftDto dto = new ParseTaskCorrectionDraftDto();
            dto.setSource("PIPELINE");
            dto.setStale(false);
            dto.setResult(resultDto);
            return dto;
        }

        @Override
        public ParseTaskCorrectionDraftDto saveCorrectionDraft(Long taskId, PipelineResultDto request) {
            ParseTaskCorrectionDraftDto dto = new ParseTaskCorrectionDraftDto();
            dto.setSource("CORRECTION");
            dto.setStale(false);
            dto.setResult(request);
            return dto;
        }

        @Override
        public void markCorrectionStale(Long taskId) {
            // no-op for controller tests
        }
    }
}
