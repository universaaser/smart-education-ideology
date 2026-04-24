package com.smartedu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.SemanticHitDto;
import com.smartedu.entity.ParseTaskIdeologyMatch;
import com.smartedu.entity.ParseTaskKnowledgePoint;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.service.VectorIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SemanticSearchControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ParseTaskKnowledgePoint knowledgePoint = new ParseTaskKnowledgePoint();
        knowledgePoint.setId(1L);
        knowledgePoint.setParseTaskId(10L);
        knowledgePoint.setPointName("IoT Security");

        ParseTaskIdeologyMatch ideologyMatch = new ParseTaskIdeologyMatch();
        ideologyMatch.setId(2L);
        ideologyMatch.setParseTaskId(10L);
        ideologyMatch.setIdeologyElement("Cyber Responsibility");

        SemanticSearchController controller = new SemanticSearchController(
                new StubVectorIndexService(),
                knowledgePointMapper(List.of(knowledgePoint)),
                ideologyMatchMapper(List.of(ideologyMatch)));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnSemanticHits() throws Exception {
        mockMvc.perform(post("/api/semantic/search")
                        .contentType("application/json")
                        .content("""
                                {
                                  "scope": "knowledge_points",
                                  "text": "iot security",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].title").value("IoT Security"))
                .andExpect(jsonPath("$.data[0].sourceType").value("parse_task_knowledge_point"));
    }

    @Test
    void shouldReturnKnowledgePointProjectionsByTaskId() throws Exception {
        mockMvc.perform(get("/api/semantic/knowledge-points").param("taskId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].pointName").value("IoT Security"));
    }

    @Test
    void shouldReturnIdeologyMatchProjectionsByTaskId() throws Exception {
        mockMvc.perform(get("/api/semantic/ideology-matches").param("taskId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].ideologyElement").value("Cyber Responsibility"));
    }

    private ParseTaskKnowledgePointMapper knowledgePointMapper(List<ParseTaskKnowledgePoint> items) {
        return (ParseTaskKnowledgePointMapper) Proxy.newProxyInstance(
                ParseTaskKnowledgePointMapper.class.getClassLoader(),
                new Class[]{ParseTaskKnowledgePointMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return items;
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

    private ParseTaskIdeologyMatchMapper ideologyMatchMapper(List<ParseTaskIdeologyMatch> items) {
        return (ParseTaskIdeologyMatchMapper) Proxy.newProxyInstance(
                ParseTaskIdeologyMatchMapper.class.getClassLoader(),
                new Class[]{ParseTaskIdeologyMatchMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return items;
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

    private static class StubVectorIndexService extends VectorIndexService {

        StubVectorIndexService() {
            super(new ObjectMapper());
        }

        @Override
        public List<SemanticHitDto> search(String collection, String query, int topK, java.util.Map<String, Object> filter) {
            return List.of(new SemanticHitDto(
                    "10_kp_0",
                    0.98,
                    "IoT Security",
                    "Industrial IoT risk control",
                    "parse_task_knowledge_point",
                    10L,
                    "People Daily",
                    "https://example.com/iot-security"));
        }
    }
}
