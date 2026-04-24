package com.smartedu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.DocumentStructureDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.entity.IdeologyKnowledge;
import com.smartedu.entity.Resource;
import com.smartedu.entity.SubjectIdeologyMatch;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.mapper.IdeologyKnowledgeMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeIngestionServiceTest {

    private static final String TAG_CRAFTSMANSHIP = "\u5de5\u5320\u7cbe\u795e";

    @Test
    void shouldNotBackfillIdeologyMatchesFromReasonWhenTagsAreEmpty() throws Exception {
        KnowledgeIngestionService service = new KnowledgeIngestionService(
                null,
                null,
                buildIdeologyMapper(),
                null,
                null,
                null,
                new ObjectMapper(),
                null);
        Resource resource = new Resource();
        resource.setTags("[]");
        resource.setIdeologySummary("This summary mentions craftsmanship but tags remain empty.");

        List<IdeologyKnowledge> resolved = invokeResolveMatchedIdeologies(service, resource);

        assertTrue(resolved.isEmpty());
    }

    @Test
    void shouldResolveExplicitIdeologyTagsOnly() throws Exception {
        KnowledgeIngestionService service = new KnowledgeIngestionService(
                null,
                null,
                buildIdeologyMapper(),
                null,
                null,
                null,
                new ObjectMapper(),
                null);
        Resource resource = new Resource();
        resource.setTags("[\"" + TAG_CRAFTSMANSHIP + "\"]");

        List<IdeologyKnowledge> resolved = invokeResolveMatchedIdeologies(service, resource);

        assertEquals(1, resolved.size());
        assertEquals(TAG_CRAFTSMANSHIP, resolved.get(0).getName());
    }

    @Test
    void shouldDeleteStoredIdeologyMatchesWhenCurrentTagListIsEmpty() throws Exception {
        DeleteRecorder recorder = new DeleteRecorder();
        KnowledgeIngestionService service = new KnowledgeIngestionService(
                null,
                null,
                buildIdeologyMapper(),
                buildSubjectIdeologyMatchMapper(recorder),
                null,
                null,
                new ObjectMapper(),
                null);
        SubjectKnowledge subjectKnowledge = new SubjectKnowledge();
        subjectKnowledge.setId(12L);

        invokeCreateIdeologyMatches(service, subjectKnowledge, List.of(), "");

        assertEquals(1, recorder.deleteCalls);
    }

    @Test
    void shouldPreferPipelineDocumentStructureForMineruTasks() throws Exception {
        KnowledgeIngestionService service = new KnowledgeIngestionService(
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                null);
        PipelineResultDto pipelineResult = new PipelineResultDto();
        DocumentStructureDto structure = new DocumentStructureDto();
        structure.setTitle("MinerU Parsed Title");
        structure.setOverview("Overview from aiAnalysis.");
        structure.setChapterOutline(List.of("Chapter 1", "Chapter 2"));
        pipelineResult.setDocumentStructure(structure);

        DocumentStructureDto resolved = invokeResolveDocumentStructure(
                service,
                pipelineResult,
                "# markdown body instead of legacy json");

        assertNotNull(resolved);
        assertEquals("MinerU Parsed Title", resolved.getTitle());
        assertEquals("Overview from aiAnalysis.", resolved.getOverview());
        assertEquals(List.of("Chapter 1", "Chapter 2"), resolved.getChapterOutline());
    }

    @Test
    void shouldFallbackToLegacyParsedContentJsonWhenPipelineStructureMissing() throws Exception {
        KnowledgeIngestionService service = new KnowledgeIngestionService(
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                null);
        DocumentStructureDto resolved = invokeResolveDocumentStructure(
                service,
                null,
                "{\"title\":\"Legacy JSON Title\",\"overview\":\"Legacy overview\",\"chapterOutline\":[\"Part A\"]}");

        assertNotNull(resolved);
        assertEquals("Legacy JSON Title", resolved.getTitle());
        assertEquals("Legacy overview", resolved.getOverview());
        assertEquals(List.of("Part A"), resolved.getChapterOutline());
    }

    @SuppressWarnings("unchecked")
    private List<IdeologyKnowledge> invokeResolveMatchedIdeologies(KnowledgeIngestionService service, Resource resource) throws Exception {
        Method method = KnowledgeIngestionService.class.getDeclaredMethod("resolveMatchedIdeologies", Resource.class);
        method.setAccessible(true);
        return (List<IdeologyKnowledge>) method.invoke(service, resource);
    }

    private void invokeCreateIdeologyMatches(
            KnowledgeIngestionService service,
            SubjectKnowledge subjectKnowledge,
            List<IdeologyKnowledge> ideologies,
            String matchReason) throws Exception {
        Method method = KnowledgeIngestionService.class.getDeclaredMethod(
                "createIdeologyMatches",
                SubjectKnowledge.class,
                List.class,
                String.class);
        method.setAccessible(true);
        method.invoke(service, subjectKnowledge, ideologies, matchReason);
    }

    private DocumentStructureDto invokeResolveDocumentStructure(
            KnowledgeIngestionService service,
            PipelineResultDto pipelineResult,
            String parsedContentJson) throws Exception {
        Method method = KnowledgeIngestionService.class.getDeclaredMethod(
                "resolveDocumentStructure",
                PipelineResultDto.class,
                String.class);
        method.setAccessible(true);
        return (DocumentStructureDto) method.invoke(service, pipelineResult, parsedContentJson);
    }

    private IdeologyKnowledgeMapper buildIdeologyMapper() {
        List<IdeologyKnowledge> ideologies = new ArrayList<>();
        IdeologyKnowledge ideology = new IdeologyKnowledge();
        ideology.setId(1L);
        ideology.setName(TAG_CRAFTSMANSHIP);
        ideology.setKeywords("\u5de5\u5320,\u7ec6\u8282");
        ideologies.add(ideology);

        return (IdeologyKnowledgeMapper) Proxy.newProxyInstance(
                IdeologyKnowledgeMapper.class.getClassLoader(),
                new Class[]{IdeologyKnowledgeMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return ideologies;
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

    private SubjectIdeologyMatchMapper buildSubjectIdeologyMatchMapper(DeleteRecorder recorder) {
        return (SubjectIdeologyMatchMapper) Proxy.newProxyInstance(
                SubjectIdeologyMatchMapper.class.getClassLoader(),
                new Class[]{SubjectIdeologyMatchMapper.class},
                (proxy, method, args) -> {
                    if ("delete".equals(method.getName())) {
                        recorder.deleteCalls++;
                        return 1;
                    }
                    if ("selectOne".equals(method.getName())) {
                        return null;
                    }
                    if ("insert".equals(method.getName()) || "updateById".equals(method.getName())) {
                        return 1;
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

    private static class DeleteRecorder {
        private int deleteCalls;
    }
}
