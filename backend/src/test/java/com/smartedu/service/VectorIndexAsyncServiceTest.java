package com.smartedu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.ResourceCitationDto;
import com.smartedu.entity.ParseTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorIndexAsyncServiceTest {

    @Test
    void shouldIndexFirstCitationWithSourceMetadata() {
        RecordingVectorIndexService vectorIndexService = new RecordingVectorIndexService();
        VectorIndexAsyncService service = new VectorIndexAsyncService(vectorIndexService);

        KnowledgePointDto point = new KnowledgePointDto();
        point.setPointName("Sensor Network");
        point.setDefinition("Industrial sensor network");
        point.setResourceCitations(List.of(
                new ResourceCitationDto("r1", 1L, "No Source", "", "", "", ""),
                new ResourceCitationDto("r2", 2L, "Valid Source", "People Daily", "https://example.com/source", "quote", "reason")));
        PipelineResultDto result = new PipelineResultDto();
        result.setKnowledgePoints(List.of(point));
        result.setIdeologyMatches(List.of());
        result.setSchemaVersion("v1");
        ParseTask task = new ParseTask();
        task.setId(42L);

        service.indexPipelineResult(task, result);

        assertEquals("People Daily", vectorIndexService.payloads.get(0).get("source"));
        assertEquals("https://example.com/source", vectorIndexService.payloads.get(0).get("source_url"));
    }

    private static class RecordingVectorIndexService extends VectorIndexService {

        private final List<Map<String, Object>> payloads = new ArrayList<>();

        RecordingVectorIndexService() {
            super(new ObjectMapper());
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            return texts.stream().map(text -> new float[]{1.0F, 0.0F}).toList();
        }

        @Override
        public String resolveCollection(String scope) {
            return scope;
        }

        @Override
        public void upsert(String collection, String id, float[] vector, Map<String, Object> payload) {
            payloads.add(payload);
        }

        @Override
        public void deleteBySource(String collection, String sourceType, Long sourceId) {
        }
    }
}
