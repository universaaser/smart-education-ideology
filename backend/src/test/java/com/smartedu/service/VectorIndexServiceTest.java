package com.smartedu.service;

import com.smartedu.dto.SemanticHitDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorIndexServiceTest {

    private VectorIndexService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new VectorIndexService(new com.fasterxml.jackson.databind.ObjectMapper());
        setField("collectionKnowledgePoints", "kp_collection");
        setField("collectionIdeologyMatches", "im_collection");
        setField("collectionSelectionExplain", "se_collection");
        setField("embeddingEnabled", false);
        setField("qdrantEnabled", false);
    }

    @Test
    void shouldReturnEmptySearchResultsWhenEmbeddingDisabled() {
        assertTrue(service.search("kp_collection", "iot", 5, null).isEmpty());
    }

    @Test
    void shouldReturnEmptyVectorSearchResultsWhenQdrantDisabled() {
        assertTrue(service.searchByVector("kp_collection", new float[]{1.0f, 2.0f}, 5, null).isEmpty());
    }

    @Test
    void shouldResolveConfiguredCollectionsByScope() {
        assertEquals("kp_collection", service.resolveCollection("knowledge_points"));
        assertEquals("im_collection", service.resolveCollection("ideology_matches"));
        assertEquals("se_collection", service.resolveCollection("selection_explain"));
    }

    @Test
    void shouldParseQdrantPayloadIntoSemanticHits() throws Exception {
        Method method = VectorIndexService.class.getDeclaredMethod("parseSearchHits", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<SemanticHitDto> hits = (List<SemanticHitDto>) method.invoke(service, """
                {
                  "result": [
                    {
                      "id": "selection_explain_1",
                      "score": 0.87,
                      "payload": {
                        "title": "sensor network",
                        "snippet": "Model reasoning body",
                        "source_type": "selection_explain_record",
                        "source_id": 12,
                        "source": "People Daily",
                        "source_url": "https://example.com/source"
                      }
                    }
                  ]
                }
                """);

        assertEquals(1, hits.size());
        assertEquals("selection_explain_1", hits.get(0).getId());
        assertEquals("sensor network", hits.get(0).getTitle());
        assertEquals("selection_explain_record", hits.get(0).getSourceType());
        assertEquals(12L, hits.get(0).getSourceId());
        assertEquals("People Daily", hits.get(0).getSource());
        assertEquals("https://example.com/source", hits.get(0).getSourceUrl());
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = VectorIndexService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
