package com.smartedu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.mapper.ParseTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        null,
        null,
        new DocumentTextExtractor(),
        new MineruParseClient(objectMapper),
        new AiStreamBuffer(),
        emptyKnowledgePointMapper(),
        emptyIdeologyMatchMapper(),
        null);
    try {
      setField("maxQuestions", 6);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
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
    Method method = AiIntelligenceService.class.getDeclaredMethod("shouldPreferResponsesApi", String.class,
        String.class);
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

    assertEquals(java.util.List.of("deepseek", "openai"), result);
  }

  @Test
  void shouldBuildTaskProviderChainFromSceneRouteAndFallbacks() throws Exception {
    setField("parseRoute", "deepseek,gemini");
    Method method = AiIntelligenceService.class.getDeclaredMethod("buildTaskProviderChain", String.class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    var result = (java.util.List<String>) method.invoke(aiIntelligenceService, AiIntelligenceService.TASK_PARSE);

    assertEquals(java.util.List.of("deepseek", "gemini", "openai"), result);
  }

  @Test
  void shouldFallbackToBackgroundChainForUnknownSceneRoute() throws Exception {
    setField("backgroundChainConfig", "openai,deepseek");
    Method method = AiIntelligenceService.class.getDeclaredMethod("buildTaskProviderChain", String.class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    var result = (java.util.List<String>) method.invoke(aiIntelligenceService, "unknown");

    assertEquals(java.util.List.of("openai", "deepseek"), result);
  }

  @Test
  void shouldFailExplicitlyForUnimplementedGeminiProvider() throws Exception {
    Method method = AiIntelligenceService.class.getDeclaredMethod("callGemini", java.util.List.class, String.class);
    method.setAccessible(true);

    try {
      method.invoke(aiIntelligenceService, java.util.List.of(), "system");
    } catch (InvocationTargetException ex) {
      assertEquals("Gemini provider is not implemented", ex.getCause().getMessage());
    }
  }

  @Test
  void shouldValidateStructuredTeachingQuestions() throws Exception {
    Method method = AiIntelligenceService.class.getDeclaredMethod("validateTeachingArtifacts", String.class);
    method.setAccessible(true);

    var result = (com.smartedu.dto.TeachingArtifactsDto) method.invoke(aiIntelligenceService, """
        {
          "lectureNotes": "Lecture",
          "cases": ["Case A"],
          "questions": [
            {
              "questionType": "SINGLE_CHOICE",
              "difficulty": "HARD",
              "knowledgePointId": 88,
              "stem": "Question A",
              "options": ["A", "B"],
              "referenceAnswer": "A",
              "scoringPoints": ["Point A"]
            }
          ]
        }
        """);

    assertEquals("SINGLE_CHOICE", result.getQuestions().get(0).getQuestionType());
    assertEquals("HARD", result.getQuestions().get(0).getDifficulty());
    assertEquals(88L, result.getQuestions().get(0).getKnowledgePointId());
    assertEquals(java.util.List.of("A", "B"), result.getQuestions().get(0).getOptions());
  }

  @Test
  void shouldRejectInvalidTeachingQuestionOptionsShape() throws Exception {
    Method method = AiIntelligenceService.class.getDeclaredMethod("validateTeachingArtifacts", String.class);
    method.setAccessible(true);

    InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> method.invoke(aiIntelligenceService, """
        {
          "lectureNotes": "Lecture",
          "cases": [],
          "questions": [
            {
              "questionType": "SINGLE_CHOICE",
              "difficulty": "EASY",
              "knowledgePointId": null,
              "stem": "Question A",
              "options": "A",
              "referenceAnswer": "Answer A",
              "scoringPoints": []
            }
          ]
        }
        """));

    assertEquals("options must be an array", ex.getCause().getMessage());
  }

  @Test
  void shouldSerializeParsedContentAsJsonWithRawMarkdown() throws Exception {
    Method method = AiIntelligenceService.class.getDeclaredMethod("writeParsedContentJson", String.class, String.class, String.class);
    method.setAccessible(true);

    String result = (String) method.invoke(aiIntelligenceService, "demo.md", "# Demo\n\nBody", "FALLBACK_LLM");
    var node = new ObjectMapper().readTree(result);

    assertEquals("demo.md", node.path("title").asText());
    assertEquals("# Demo\n\nBody", node.path("rawMarkdown").asText());
    assertEquals("FALLBACK_LLM", node.path("parseMode").asText());
  }

  @Test
  void shouldExtractChatCompletionTextFromArrayContent() throws Exception {
    Method method = AiIntelligenceService.class.getDeclaredMethod("extractTextContent",
        com.fasterxml.jackson.databind.JsonNode.class);
    method.setAccessible(true);
    ObjectMapper objectMapper = new ObjectMapper();
    var node = objectMapper.readTree("""
        [
          { "type": "text", "text": "Hello" },
          { "type": "text", "text": "World" }
        ]
        """);

    String result = (String) method.invoke(aiIntelligenceService, node);

    assertEquals("Hello\nWorld", result);
  }

  @Test
  void shouldExtractResponsesTextFromOutputArray() throws Exception {
    Method method = AiIntelligenceService.class.getDeclaredMethod("extractResponsesContent",
        com.fasterxml.jackson.databind.JsonNode.class);
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

  private void setField(String fieldName, Object value) throws Exception {
    Field field = AiIntelligenceService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(aiIntelligenceService, value);
  }

  private ParseTaskMapper buildMapperStub(ParseTask task) {
    return (ParseTaskMapper) Proxy.newProxyInstance(
        ParseTaskMapper.class.getClassLoader(),
        new Class[] { ParseTaskMapper.class },
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

  private ParseTaskKnowledgePointMapper emptyKnowledgePointMapper() {
    return (ParseTaskKnowledgePointMapper) Proxy.newProxyInstance(
        ParseTaskKnowledgePointMapper.class.getClassLoader(),
        new Class[] { ParseTaskKnowledgePointMapper.class },
        (proxy, method, args) -> primitiveDefault(method.getReturnType()));
  }

  private ParseTaskIdeologyMatchMapper emptyIdeologyMatchMapper() {
    return (ParseTaskIdeologyMatchMapper) Proxy.newProxyInstance(
        ParseTaskIdeologyMatchMapper.class.getClassLoader(),
        new Class[] { ParseTaskIdeologyMatchMapper.class },
        (proxy, method, args) -> primitiveDefault(method.getReturnType()));
  }

  private Object primitiveDefault(Class<?> returnType) {
    if (returnType.equals(boolean.class)) {
      return false;
    }
    if (returnType.isPrimitive()) {
      return 0;
    }
    return null;
  }
}
