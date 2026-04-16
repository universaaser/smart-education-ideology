package com.smartedu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.DocumentStructureDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.TeachingMaterialMapper;
import com.smartedu.mapper.TeachingMaterialTraceMapper;
import com.smartedu.service.AiPipelineJsonValidator;
import com.smartedu.service.AiIntelligenceService;
import com.smartedu.service.TeachingMaterialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.util.ArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerTest {

    private MockMvc mockMvc;
    private PipelineResultDto resultDto;

    @BeforeEach
    void setUp() {
        ParseTask task = new ParseTask();
        task.setId(1L);

        ParseTaskMapper parseTaskMapper = buildMapperStub(task);

        resultDto = new PipelineResultDto();
        DocumentStructureDto structureDto = new DocumentStructureDto();
        structureDto.setTitle("IoT Teaching Outline");
        resultDto.setDocumentStructure(structureDto);
        resultDto.setWarnings(new ArrayList<>());
        resultDto.setSchemaVersion("v1");

        AiIntelligenceService aiIntelligenceService = new StubAiService(resultDto);
        TeachingMaterialService teachingMaterialService = new TeachingMaterialService(
                (TeachingMaterialMapper) Proxy.newProxyInstance(
                        TeachingMaterialMapper.class.getClassLoader(),
                        new Class[]{TeachingMaterialMapper.class},
                        (proxy, method, args) -> {
                            if ("selectList".equals(method.getName())) {
                                return new ArrayList<>();
                            }
                            return null;
                        }
                ),
                parseTaskMapper,
                (SubjectKnowledgeMapper) Proxy.newProxyInstance(
                        SubjectKnowledgeMapper.class.getClassLoader(),
                        new Class[]{SubjectKnowledgeMapper.class},
                        (proxy, method, args) -> null
                ),
                aiIntelligenceService,
                (TeachingMaterialTraceMapper) Proxy.newProxyInstance(
                        TeachingMaterialTraceMapper.class.getClassLoader(),
                        new Class[]{TeachingMaterialTraceMapper.class},
                        (proxy, method, args) -> null
                ),
                new ObjectMapper()
        );
        UploadController controller = new UploadController(parseTaskMapper, aiIntelligenceService, teachingMaterialService);
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

    private ParseTaskMapper buildMapperStub(ParseTask task) {
        return (ParseTaskMapper) Proxy.newProxyInstance(
                ParseTaskMapper.class.getClassLoader(),
                new Class[]{ParseTaskMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return task;
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

        StubAiService(PipelineResultDto pipelineResultDto) {
            super(
                    (ParseTaskMapper) Proxy.newProxyInstance(
                            ParseTaskMapper.class.getClassLoader(),
                            new Class[]{ParseTaskMapper.class},
                            (proxy, method, args) -> null),
                    new ObjectMapper(),
                    null,
                    new AiPipelineJsonValidator(new ObjectMapper()),
                    null
            );
            this.pipelineResultDto = pipelineResultDto;
        }

        @Override
        public PipelineResultDto getPipelineResult(Long taskId) {
            return pipelineResultDto;
        }

        @Override
        public PipelineResultDto regenerateTask(Long taskId) {
            return pipelineResultDto;
        }
    }
}
