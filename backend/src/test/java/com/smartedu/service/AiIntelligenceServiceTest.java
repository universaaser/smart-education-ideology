package com.smartedu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.mapper.ParseTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiIntelligenceServiceTest {

    private AiIntelligenceService aiIntelligenceService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        KnowledgeIngestionService knowledgeIngestionService = null;
        AiPipelineJsonValidator validator = new AiPipelineJsonValidator(objectMapper);
        ParseTask task = new ParseTask();
        task.setId(1L);
        task.setAiAnalysis("""
                {
                  "documentStructure": {
                    "title": "IoT Outline",
                    "documentType": "OUTLINE",
                    "overview": "Overview",
                    "chapterOutline": ["Chapter A"],
                    "teachingFocus": ["Focus A"]
                  },
                  "knowledgePoints": [],
                  "ideologyMatches": [],
                  "teachingArtifacts": {
                    "lectureNotes": "Lecture",
                    "cases": ["Case A"],
                    "questions": []
                  },
                  "warnings": [],
                  "inferred": false,
                  "schemaVersion": "v1"
                }
                """);
        task.setParsedContent("""
                {
                  "title": "IoT Outline",
                  "documentType": "OUTLINE",
                  "overview": "Overview",
                  "chapterOutline": ["Chapter A"],
                  "teachingFocus": ["Focus A"]
                }
                """);
        ParseTaskMapper parseTaskMapper = buildMapperStub(task);

        aiIntelligenceService = new AiIntelligenceService(
                parseTaskMapper,
                objectMapper,
                knowledgeIngestionService,
                validator,
                null
        );
    }

    @Test
    void shouldReturnPipelineResultFromStoredJson() {
        PipelineResultDto result = aiIntelligenceService.getPipelineResult(1L);
        assertNotNull(result);
        assertNotNull(result.getDocumentStructure());
        assertEquals("IoT Outline", result.getDocumentStructure().getTitle());
        assertEquals("v1", result.getSchemaVersion());
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
}
