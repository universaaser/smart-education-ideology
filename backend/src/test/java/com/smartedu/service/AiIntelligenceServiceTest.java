package com.smartedu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.mapper.ParseTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldPreferResponsesApiForGpt5Models() throws Exception {
        Method method = AiIntelligenceService.class.getDeclaredMethod("shouldPreferResponsesApi", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(aiIntelligenceService, "gpt-5.4");

        assertTrue(result);
    }

    @Test
    void shouldSkipResponsesApiForLocalOpenAiCompatibleBaseUrl() throws Exception {
        Method method = AiIntelligenceService.class.getDeclaredMethod("shouldPreferResponsesApi", String.class, String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(aiIntelligenceService, "http://localhost:8317/v1", "gpt-5.4");

        assertFalse(result);
    }

    @Test
    void shouldNormalizeLegacyPreferredProvidersToOpenAi() throws Exception {
        Method method = AiIntelligenceService.class.getDeclaredMethod("buildPreferredProviderChain", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        var result = (java.util.List<String>) method.invoke(aiIntelligenceService, "deepseek");

        assertEquals(java.util.List.of("openai"), result);
    }

    @Test
    void shouldExtractChatCompletionTextFromArrayContent() throws Exception {
        Method method = AiIntelligenceService.class.getDeclaredMethod("extractChatCompletionContent", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        ObjectMapper objectMapper = new ObjectMapper();
        var node = objectMapper.readTree("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": [
                          { "type": "text", "text": "Hello" },
                          { "type": "text", "text": "World" }
                        ]
                      }
                    }
                  ]
                }
                """);

        String result = (String) method.invoke(aiIntelligenceService, node);

        assertEquals("Hello\nWorld", result);
    }

    @Test
    void shouldExtractResponsesTextFromOutputArray() throws Exception {
        Method method = AiIntelligenceService.class.getDeclaredMethod("extractResponsesContent", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        ObjectMapper objectMapper = new ObjectMapper();
        var node = objectMapper.readTree("""
                {
                  "output": [
                    {
                      "content": [
                        { "type": "output_text", "text": "Answer line 1" },
                        { "type": "output_text", "text": "Answer line 2" }
                      ]
                    }
                  ]
                }
                """);

        String result = (String) method.invoke(aiIntelligenceService, node);

        assertEquals("Answer line 1\nAnswer line 2", result);
    }

    @Test
    void shouldExtractServiceErrorMessageFromNestedObject() throws Exception {
        Method method = AiIntelligenceService.class.getDeclaredMethod("extractServiceErrorMessage", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(aiIntelligenceService, "{\"error\":{\"message\":\"bad request\"}}");

        assertEquals("bad request", result);
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
