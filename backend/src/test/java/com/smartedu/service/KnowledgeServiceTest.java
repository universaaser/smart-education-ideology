package com.smartedu.service;

import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.entity.IdeologyKnowledge;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.mapper.CourseSubjectKnowledgeMapper;
import com.smartedu.mapper.IdeologyKnowledgeMapper;
import com.smartedu.mapper.KnowledgeRelationMapper;
import com.smartedu.mapper.StudentActivityMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.SubjectKnowledgeSourceMapper;
import com.smartedu.mapper.TeachingMaterialTraceMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KnowledgeServiceTest {

    @Test
    void shouldDetachDependentRecordsBeforeDeletingSubjectNode() {
        DeleteRecorder recorder = new DeleteRecorder();
        RecordingKnowledgeChunkService chunkService = new RecordingKnowledgeChunkService();
        KnowledgeService service = new KnowledgeService(
                buildSubjectKnowledgeMapper(recorder),
                null,
                buildSubjectIdeologyMatchMapper(recorder),
                buildKnowledgeRelationMapper(recorder),
                null,
                chunkService,
                buildCourseSubjectKnowledgeMapper(recorder),
                buildSubjectKnowledgeSourceMapper(recorder),
                buildTeachingMaterialTraceMapper(recorder),
                buildStudentActivityMapper(recorder)
        );

        service.deleteNode(42L);

        assertEquals(1, recorder.relationDeleteCalls);
        assertEquals(1, recorder.matchDeleteCalls);
        assertEquals(1, recorder.courseSubjectDeleteCalls);
        assertEquals(1, recorder.subjectSourceDeleteCalls);
        assertEquals(1, recorder.traceUpdateCalls);
        assertEquals(1, recorder.studentActivityUpdateCalls);
        assertEquals(1, recorder.subjectDeleteCalls);
        assertEquals(42L, recorder.deletedSubjectId);
        assertEquals("SUBJECT_KNOWLEDGE", chunkService.lastSourceType);
        assertEquals(42L, chunkService.lastSourceId);
    }

    @Test
    void shouldIgnoreIdeologyGraphNodeDeletion() {
        DeleteRecorder recorder = new DeleteRecorder();
        RecordingKnowledgeChunkService chunkService = new RecordingKnowledgeChunkService();
        KnowledgeService service = new KnowledgeService(
                buildSubjectKnowledgeMapper(recorder),
                null,
                buildSubjectIdeologyMatchMapper(recorder),
                buildKnowledgeRelationMapper(recorder),
                null,
                chunkService,
                buildCourseSubjectKnowledgeMapper(recorder),
                buildSubjectKnowledgeSourceMapper(recorder),
                buildTeachingMaterialTraceMapper(recorder),
                buildStudentActivityMapper(recorder)
        );

        service.deleteNode(-9L);

        assertEquals(0, recorder.relationDeleteCalls);
        assertEquals(0, recorder.matchDeleteCalls);
        assertEquals(0, recorder.courseSubjectDeleteCalls);
        assertEquals(0, recorder.subjectSourceDeleteCalls);
        assertEquals(0, recorder.traceUpdateCalls);
        assertEquals(0, recorder.studentActivityUpdateCalls);
        assertEquals(0, recorder.subjectDeleteCalls);
        assertNull(chunkService.lastSourceType);
        assertNull(chunkService.lastSourceId);
    }

    @Test
    void shouldPlaceCreatedNodeAwayFromExistingNodesAndKeepRequestedSize() {
        CreateNodeRecorder recorder = new CreateNodeRecorder();
        RecordingKnowledgeChunkService chunkService = new RecordingKnowledgeChunkService();
        List<SubjectKnowledge> existingSubjects = new ArrayList<>();

        SubjectKnowledge existingSubject = new SubjectKnowledge();
        existingSubject.setId(1L);
        existingSubject.setName("Existing Subject");
        existingSubject.setPositionX(0D);
        existingSubject.setPositionY(0D);
        existingSubject.setNodeSize("MD");
        existingSubjects.add(existingSubject);

        IdeologyKnowledge existingIdeology = new IdeologyKnowledge();
        existingIdeology.setId(2L);
        existingIdeology.setName("Existing Ideology");
        existingIdeology.setPositionX(240D);
        existingIdeology.setPositionY(0D);
        existingIdeology.setNodeSize("MD");

        KnowledgeService service = new KnowledgeService(
                buildCreateSubjectKnowledgeMapper(existingSubjects, recorder),
                buildIdeologyKnowledgeMapper(List.of(existingIdeology)),
                buildSubjectIdeologyMatchMapper(new DeleteRecorder()),
                buildKnowledgeRelationMapper(new DeleteRecorder()),
                null,
                chunkService,
                buildCourseSubjectKnowledgeMapper(new DeleteRecorder()),
                buildSubjectKnowledgeSourceMapper(new DeleteRecorder()),
                buildTeachingMaterialTraceMapper(new DeleteRecorder()),
                buildStudentActivityMapper(new DeleteRecorder())
        );

        KnowledgeNodeView node = new KnowledgeNodeView();
        node.setName("A very long subject node title");
        node.setNodeSize("LG");

        KnowledgeNodeView created = service.createNode(node);

        assertEquals("LG", recorder.insertedSubject.getNodeSize());
        assertNotEquals(0D, recorder.insertedSubject.getPositionX());
        assertEquals(recorder.insertedSubject.getPositionX(), created.getPositionX());
        assertEquals(recorder.insertedSubject.getPositionY(), created.getPositionY());
    }

    private SubjectKnowledgeMapper buildSubjectKnowledgeMapper(DeleteRecorder recorder) {
        return (SubjectKnowledgeMapper) Proxy.newProxyInstance(
                SubjectKnowledgeMapper.class.getClassLoader(),
                new Class[]{SubjectKnowledgeMapper.class},
                (proxy, method, args) -> {
                    if ("deleteById".equals(method.getName())) {
                        recorder.subjectDeleteCalls++;
                        recorder.deletedSubjectId = (Long) args[0];
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private SubjectKnowledgeMapper buildCreateSubjectKnowledgeMapper(List<SubjectKnowledge> existingSubjects, CreateNodeRecorder recorder) {
        return (SubjectKnowledgeMapper) Proxy.newProxyInstance(
                SubjectKnowledgeMapper.class.getClassLoader(),
                new Class[]{SubjectKnowledgeMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return existingSubjects;
                    }
                    if ("insert".equals(method.getName())) {
                        SubjectKnowledge subject = (SubjectKnowledge) args[0];
                        subject.setId(99L);
                        recorder.insertedSubject = subject;
                        existingSubjects.add(subject);
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private IdeologyKnowledgeMapper buildIdeologyKnowledgeMapper(List<IdeologyKnowledge> ideologies) {
        return (IdeologyKnowledgeMapper) Proxy.newProxyInstance(
                IdeologyKnowledgeMapper.class.getClassLoader(),
                new Class[]{IdeologyKnowledgeMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return ideologies;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private SubjectIdeologyMatchMapper buildSubjectIdeologyMatchMapper(DeleteRecorder recorder) {
        return (SubjectIdeologyMatchMapper) Proxy.newProxyInstance(
                SubjectIdeologyMatchMapper.class.getClassLoader(),
                new Class[]{SubjectIdeologyMatchMapper.class},
                (proxy, method, args) -> {
                    if ("delete".equals(method.getName())) {
                        recorder.matchDeleteCalls++;
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private KnowledgeRelationMapper buildKnowledgeRelationMapper(DeleteRecorder recorder) {
        return (KnowledgeRelationMapper) Proxy.newProxyInstance(
                KnowledgeRelationMapper.class.getClassLoader(),
                new Class[]{KnowledgeRelationMapper.class},
                (proxy, method, args) -> {
                    if ("delete".equals(method.getName())) {
                        recorder.relationDeleteCalls++;
                        return 1;
                    }
                    if ("selectList".equals(method.getName())) {
                        return List.of();
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private CourseSubjectKnowledgeMapper buildCourseSubjectKnowledgeMapper(DeleteRecorder recorder) {
        return (CourseSubjectKnowledgeMapper) Proxy.newProxyInstance(
                CourseSubjectKnowledgeMapper.class.getClassLoader(),
                new Class[]{CourseSubjectKnowledgeMapper.class},
                (proxy, method, args) -> {
                    if ("delete".equals(method.getName())) {
                        recorder.courseSubjectDeleteCalls++;
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private SubjectKnowledgeSourceMapper buildSubjectKnowledgeSourceMapper(DeleteRecorder recorder) {
        return (SubjectKnowledgeSourceMapper) Proxy.newProxyInstance(
                SubjectKnowledgeSourceMapper.class.getClassLoader(),
                new Class[]{SubjectKnowledgeSourceMapper.class},
                (proxy, method, args) -> {
                    if ("delete".equals(method.getName())) {
                        recorder.subjectSourceDeleteCalls++;
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private TeachingMaterialTraceMapper buildTeachingMaterialTraceMapper(DeleteRecorder recorder) {
        return (TeachingMaterialTraceMapper) Proxy.newProxyInstance(
                TeachingMaterialTraceMapper.class.getClassLoader(),
                new Class[]{TeachingMaterialTraceMapper.class},
                (proxy, method, args) -> {
                    if ("update".equals(method.getName())) {
                        recorder.traceUpdateCalls++;
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private StudentActivityMapper buildStudentActivityMapper(DeleteRecorder recorder) {
        return (StudentActivityMapper) Proxy.newProxyInstance(
                StudentActivityMapper.class.getClassLoader(),
                new Class[]{StudentActivityMapper.class},
                (proxy, method, args) -> {
                    if ("update".equals(method.getName())) {
                        recorder.studentActivityUpdateCalls++;
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(int.class)) {
            return 0;
        }
        if (returnType.equals(long.class)) {
            return 0L;
        }
        return null;
    }

    private static class DeleteRecorder {
        private int relationDeleteCalls;
        private int matchDeleteCalls;
        private int courseSubjectDeleteCalls;
        private int subjectSourceDeleteCalls;
        private int traceUpdateCalls;
        private int studentActivityUpdateCalls;
        private int subjectDeleteCalls;
        private Long deletedSubjectId;
    }

    private static class CreateNodeRecorder {
        private SubjectKnowledge insertedSubject;
    }

    private static class RecordingKnowledgeChunkService extends KnowledgeChunkService {
        private String lastSourceType;
        private Long lastSourceId;
        private SubjectKnowledge refreshedSubject;

        RecordingKnowledgeChunkService() {
            super(null, null, null);
        }

        @Override
        public void refreshSubjectKnowledgeChunks(SubjectKnowledge subjectKnowledge) {
            refreshedSubject = subjectKnowledge;
        }

        @Override
        public void deleteChunks(String sourceType, Long sourceId) {
            lastSourceType = sourceType;
            lastSourceId = sourceId;
        }
    }
}
