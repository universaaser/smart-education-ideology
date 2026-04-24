package com.smartedu.controller;

import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.service.KnowledgeService;
import com.smartedu.service.PathRecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PathRecommendControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PathRecommendController controller = new PathRecommendController(
                new StubPathRecommendService(),
                new StubKnowledgeService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnRecommendedPathFromStructuredRequest() throws Exception {
        mockMvc.perform(post("/api/path/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": 9,
                                  "masteredNodeIds": [1, 2],
                                  "interestTags": ["iot"],
                                  "maxLength": 6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodeIds[0]").value(11))
                .andExpect(jsonPath("$.data.nodeNames[0]").value("Node-11"));
    }

    @Test
    void shouldRejectMissingStudentId() throws Exception {
        mockMvc.perform(post("/api/path/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "masteredNodeIds": [1, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Student id cannot be empty"));
    }

    private static class StubPathRecommendService extends PathRecommendService {

        StubPathRecommendService() {
            super(null, null, null);
        }

        @Override
        public List<Long> generateLearningPath(Long studentId, Set<Long> masteredNodeIds, List<String> interestTags, int maxLength) {
            return List.of(11L, 22L);
        }
    }

    private static class StubKnowledgeService extends KnowledgeService {

        StubKnowledgeService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public KnowledgeNodeView getNodeById(Long id) {
            return new KnowledgeNodeView(id, "Node-" + id, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
