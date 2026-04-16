package com.smartedu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.TeachingMaterialViewDto;
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
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeachingMaterialControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TeachingMaterialController controller = new TeachingMaterialController(new StubTeachingMaterialService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnMaterialDetail() throws Exception {
        mockMvc.perform(get("/api/materials/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.materialId").value(1))
                .andExpect(jsonPath("$.data.versionNo").value(2));
    }

    @Test
    void shouldExportMarkdownWithHeaders() throws Exception {
        mockMvc.perform(get("/api/materials/1/export/markdown"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"iot-outline-v2.md\""))
                .andExpect(content().string("# IoT Outline"));
    }

    private static class StubTeachingMaterialService extends TeachingMaterialService {

        StubTeachingMaterialService() {
            super(
                    (TeachingMaterialMapper) Proxy.newProxyInstance(
                            TeachingMaterialMapper.class.getClassLoader(),
                            new Class[]{TeachingMaterialMapper.class},
                            (proxy, method, args) -> null
                    ),
                    (ParseTaskMapper) Proxy.newProxyInstance(
                            ParseTaskMapper.class.getClassLoader(),
                            new Class[]{ParseTaskMapper.class},
                            (proxy, method, args) -> null
                    ),
                    (SubjectKnowledgeMapper) Proxy.newProxyInstance(
                            SubjectKnowledgeMapper.class.getClassLoader(),
                            new Class[]{SubjectKnowledgeMapper.class},
                            (proxy, method, args) -> null
                    ),
                    new AiIntelligenceService(
                            (ParseTaskMapper) Proxy.newProxyInstance(
                                    ParseTaskMapper.class.getClassLoader(),
                                    new Class[]{ParseTaskMapper.class},
                                    (proxy, method, args) -> null),
                            new ObjectMapper(),
                            null,
                            new AiPipelineJsonValidator(new ObjectMapper()),
                            null
                    ),
                    (TeachingMaterialTraceMapper) Proxy.newProxyInstance(
                            TeachingMaterialTraceMapper.class.getClassLoader(),
                            new Class[]{TeachingMaterialTraceMapper.class},
                            (proxy, method, args) -> null
                    ),
                    new ObjectMapper()
            );
        }

        @Override
        public TeachingMaterialViewDto getMaterialById(Long materialId) {
            TeachingMaterialViewDto dto = new TeachingMaterialViewDto();
            dto.setMaterialId(materialId);
            dto.setParseTaskId(1L);
            dto.setUserId(1L);
            dto.setTitle("IoT Outline");
            dto.setVersionNo(2);
            dto.setStatus("PUBLISHED");
            dto.setUpdatedAt(LocalDateTime.now());
            return dto;
        }

        @Override
        public String exportMarkdownByMaterialId(Long materialId) {
            return "# IoT Outline";
        }

        @Override
        public String buildMarkdownFileName(Long materialId) {
            return "iot-outline-v2.md";
        }
    }
}
