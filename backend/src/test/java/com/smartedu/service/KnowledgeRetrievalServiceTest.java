package com.smartedu.service;

import com.smartedu.entity.KnowledgeChunk;
import com.smartedu.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeRetrievalServiceTest {

    @Test
    void shouldReturnFoundWhenFullTextHits() {
        KnowledgeChunk chunk = buildChunk("FULLTEXT Sensor Evidence", 2.0D);
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                buildChunkMapper(List.of(chunk), List.of()),
                null,
                null,
                null,
                null,
                null,
                null);

        var result = service.retrieveWithStatus("sensor", 5);

        assertEquals(KnowledgeRetrievalService.STATUS_FOUND, result.getRetrievalStatus());
        assertEquals(1, result.getContexts().size());
        assertEquals("FULLTEXT", result.getContexts().get(0).getMatchedBy());
    }

    @Test
    void shouldReturnWeakMatchWhenOnlyLikeHits() {
        KnowledgeChunk chunk = buildChunk("LIKE Sensor Evidence", null);
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                buildChunkMapper(List.of(), List.of(chunk)),
                null,
                null,
                null,
                null,
                null,
                null);

        var result = service.retrieveWithStatus("sensor", 5);

        assertEquals(KnowledgeRetrievalService.STATUS_WEAK_MATCH, result.getRetrievalStatus());
        assertEquals("LIKE", result.getContexts().get(0).getMatchedBy());
    }

    @Test
    void shouldReturnNoContextWhenNothingHits() {
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                buildChunkMapper(List.of(), List.of()),
                null,
                null,
                null,
                null,
                null,
                null);

        var result = service.retrieveWithStatus("unknown", 5);

        assertEquals(KnowledgeRetrievalService.STATUS_NO_CONTEXT, result.getRetrievalStatus());
        assertEquals(0, result.getContexts().size());
    }

    private KnowledgeChunkMapper buildChunkMapper(List<KnowledgeChunk> fullText, List<KnowledgeChunk> like) {
        return (KnowledgeChunkMapper) Proxy.newProxyInstance(
                KnowledgeChunkMapper.class.getClassLoader(),
                new Class[]{KnowledgeChunkMapper.class},
                (proxy, method, args) -> {
                    if ("searchFullText".equals(method.getName())) {
                        return fullText;
                    }
                    if ("selectList".equals(method.getName())) {
                        return like;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }

    private KnowledgeChunk buildChunk(String title, Double score) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setSourceType("RESOURCE");
        chunk.setSourceId(1L);
        chunk.setChunkIndex(0);
        chunk.setTitle(title);
        chunk.setContent("Sensor evidence content.");
        chunk.setSource("People Daily");
        chunk.setSourceUrl("https://example.com");
        chunk.setSearchScore(score);
        return chunk;
    }
}
