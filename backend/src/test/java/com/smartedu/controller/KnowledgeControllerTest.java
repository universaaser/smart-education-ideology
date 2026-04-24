package com.smartedu.controller;

import com.smartedu.service.KnowledgeExcelService;
import com.smartedu.service.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeControllerTest {

    private MockMvc mockMvc;
    private StubKnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = new StubKnowledgeService();
        KnowledgeController controller = new KnowledgeController(knowledgeService, new StubKnowledgeExcelService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldUpdateNodePositionFromStructuredRequest() throws Exception {
        mockMvc.perform(patch("/api/knowledge/nodes/12/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "x": 12.5,
                                  "y": 18.75
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(12L, knowledgeService.lastNodeId);
        assertEquals(12.5D, knowledgeService.lastX);
        assertEquals(18.75D, knowledgeService.lastY);
    }

    @Test
    void shouldDeleteNodeById() throws Exception {
        mockMvc.perform(delete("/api/knowledge/nodes/15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(15L, knowledgeService.deletedNodeId);
    }

    private static class StubKnowledgeService extends KnowledgeService {

        private Long lastNodeId;
        private Double lastX;
        private Double lastY;
        private Long deletedNodeId;

        StubKnowledgeService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void updateNodePosition(Long id, Double x, Double y) {
            lastNodeId = id;
            lastX = x;
            lastY = y;
        }

        @Override
        public void deleteNode(Long id) {
            deletedNodeId = id;
        }
    }

    private static class StubKnowledgeExcelService extends KnowledgeExcelService {

        StubKnowledgeExcelService() {
            super(null);
        }
    }
}
