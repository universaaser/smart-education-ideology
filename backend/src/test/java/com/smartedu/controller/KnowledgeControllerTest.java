package com.smartedu.controller;

import com.smartedu.entity.KnowledgeRelation;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void shouldUpdateRelation() throws Exception {
        mockMvc.perform(put("/api/knowledge/relations/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "relationType": "VALUE_SHOW",
                                  "description": "Updated relation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.relationType").value("VALUE_SHOW"));

        assertEquals(7L, knowledgeService.updatedRelationId);
    }

    @Test
    void shouldReturnBadRequestForDuplicateRelation() throws Exception {
        mockMvc.perform(post("/api/knowledge/relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromNodeId": 1,
                                  "toNodeId": 2,
                                  "relationType": "TECH_BASE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Duplicate relation"));
    }

    @Test
    void shouldDeleteRelation() throws Exception {
        mockMvc.perform(delete("/api/knowledge/relations/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(8L, knowledgeService.deletedRelationId);
    }

    @Test
    void shouldUndoLatestRelationChange() throws Exception {
        mockMvc.perform(post("/api/knowledge/relations/undo-latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.relationType").value("THEORY_SUPPORT"));
    }

    private static class StubKnowledgeService extends KnowledgeService {

        private Long lastNodeId;
        private Double lastX;
        private Double lastY;
        private Long deletedNodeId;
        private Long updatedRelationId;
        private Long deletedRelationId;

        StubKnowledgeService() {
            super(null, null, null, null, null, null, null, null, null, null);
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

        @Override
        public KnowledgeRelation createRelation(KnowledgeRelation relation) {
            throw new IllegalArgumentException("Duplicate relation");
        }

        @Override
        public KnowledgeRelation updateRelation(Long id, KnowledgeRelation request) {
            updatedRelationId = id;
            KnowledgeRelation relation = new KnowledgeRelation();
            relation.setId(id);
            relation.setRelationType(request.getRelationType());
            relation.setDescription(request.getDescription());
            return relation;
        }

        @Override
        public void deleteRelation(Long id) {
            deletedRelationId = id;
        }

        @Override
        public KnowledgeRelation undoLatestRelationChange() {
            KnowledgeRelation relation = new KnowledgeRelation();
            relation.setId(9L);
            relation.setRelationType("THEORY_SUPPORT");
            return relation;
        }
    }

    private static class StubKnowledgeExcelService extends KnowledgeExcelService {

        StubKnowledgeExcelService() {
            super(null);
        }
    }
}
