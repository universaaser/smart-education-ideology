package com.smartedu.service;

import com.smartedu.entity.KnowledgeChunk;
import com.smartedu.entity.Resource;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.mapper.KnowledgeChunkMapper;
import com.smartedu.mapper.ResourceMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkServiceTest {

    @Test
    void shouldBuildChunksFromResourceAndSubjectKnowledge() {
        ChunkStore store = new ChunkStore();
        KnowledgeChunkService service = new KnowledgeChunkService(
                buildChunkMapper(store),
                buildResourceMapper(List.of()),
                buildSubjectKnowledgeMapper(List.of()));

        Resource resource = new Resource();
        resource.setId(1L);
        resource.setTitle("Sensor Network");
        resource.setContent("Industrial sensor network content.");
        resource.setIdeologySummary("National strategy summary.");
        resource.setSource("People Daily");
        resource.setSourceUrl("https://example.com/resource");

        SubjectKnowledge subjectKnowledge = new SubjectKnowledge();
        subjectKnowledge.setId(2L);
        subjectKnowledge.setName("Edge Computing");
        subjectKnowledge.setSummary("Edge computing summary.");
        subjectKnowledge.setIdeologySummary("Craftsmanship mapping.");
        subjectKnowledge.setSubject("IoT");
        subjectKnowledge.setSourceUrl("https://example.com/subject");

        service.refreshResourceChunks(resource);
        service.refreshSubjectKnowledgeChunks(subjectKnowledge);

        assertEquals(4, store.inserted.size());
        assertTrue(store.inserted.stream().anyMatch(chunk -> "RESOURCE".equals(chunk.getSourceType())));
        assertTrue(store.inserted.stream().anyMatch(chunk -> "SUBJECT_KNOWLEDGE".equals(chunk.getSourceType())));
    }

    private KnowledgeChunkMapper buildChunkMapper(ChunkStore store) {
        return (KnowledgeChunkMapper) Proxy.newProxyInstance(
                KnowledgeChunkMapper.class.getClassLoader(),
                new Class[]{KnowledgeChunkMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName()) && args[0] instanceof KnowledgeChunk chunk) {
                        store.inserted.add(chunk);
                        return 1;
                    }
                    if ("delete".equals(method.getName())) {
                        return 1;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }

    private ResourceMapper buildResourceMapper(List<Resource> resources) {
        return (ResourceMapper) Proxy.newProxyInstance(
                ResourceMapper.class.getClassLoader(),
                new Class[]{ResourceMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName()) ? resources : null);
    }

    private SubjectKnowledgeMapper buildSubjectKnowledgeMapper(List<SubjectKnowledge> subjects) {
        return (SubjectKnowledgeMapper) Proxy.newProxyInstance(
                SubjectKnowledgeMapper.class.getClassLoader(),
                new Class[]{SubjectKnowledgeMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName()) ? subjects : null);
    }

    private static class ChunkStore {
        private final List<KnowledgeChunk> inserted = new ArrayList<>();
    }
}
