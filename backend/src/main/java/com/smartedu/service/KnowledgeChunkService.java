package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.entity.KnowledgeChunk;
import com.smartedu.entity.Resource;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.mapper.KnowledgeChunkMapper;
import com.smartedu.mapper.ResourceMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeChunkService {

    private static final String SOURCE_RESOURCE = "RESOURCE";
    private static final String SOURCE_SUBJECT_KNOWLEDGE = "SUBJECT_KNOWLEDGE";
    private static final int MAX_CHUNK_LENGTH = 700;

    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final ResourceMapper resourceMapper;
    private final SubjectKnowledgeMapper subjectKnowledgeMapper;

    @Transactional
    public void refreshResourceChunks(Resource resource) {
        if (resource == null || resource.getId() == null) {
            return;
        }
        replaceChunks(SOURCE_RESOURCE, resource.getId(), buildResourceChunks(resource));
    }

    @Transactional
    public void refreshSubjectKnowledgeChunks(SubjectKnowledge subjectKnowledge) {
        if (subjectKnowledge == null || subjectKnowledge.getId() == null) {
            return;
        }
        replaceChunks(SOURCE_SUBJECT_KNOWLEDGE, subjectKnowledge.getId(), buildSubjectKnowledgeChunks(subjectKnowledge));
    }

    @Transactional
    public void deleteChunks(String sourceType, Long sourceId) {
        if (sourceType == null || sourceType.isBlank() || sourceId == null) {
            return;
        }
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getSourceType, sourceType)
                .eq(KnowledgeChunk::getSourceId, sourceId);
        knowledgeChunkMapper.delete(wrapper);
    }

    @Transactional
    public int rebuildAllChunks() {
        int count = 0;
        for (Resource resource : resourceMapper.selectList(new LambdaQueryWrapper<>())) {
            refreshResourceChunks(resource);
            count++;
        }
        for (SubjectKnowledge subjectKnowledge : subjectKnowledgeMapper.selectList(new LambdaQueryWrapper<>())) {
            refreshSubjectKnowledgeChunks(subjectKnowledge);
            count++;
        }
        return count;
    }

    private void replaceChunks(String sourceType, Long sourceId, List<KnowledgeChunk> chunks) {
        deleteChunks(sourceType, sourceId);
        for (KnowledgeChunk chunk : chunks) {
            chunk.setCreatedAt(LocalDateTime.now());
            chunk.setUpdatedAt(LocalDateTime.now());
            knowledgeChunkMapper.insert(chunk);
        }
    }

    private List<KnowledgeChunk> buildResourceChunks(Resource resource) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int index = 0;
        for (String content : splitContent(resource.getContent())) {
            chunks.add(buildChunk(
                    SOURCE_RESOURCE,
                    resource.getId(),
                    index++,
                    resource.getTitle(),
                    content,
                    resource.getSource(),
                    resource.getSourceUrl(),
                    null,
                    resource.getTitle(),
                    ""));
        }
        for (String content : splitContent(resource.getIdeologySummary())) {
            chunks.add(buildChunk(
                    SOURCE_RESOURCE,
                    resource.getId(),
                    index++,
                    resource.getTitle(),
                    content,
                    resource.getSource(),
                    resource.getSourceUrl(),
                    null,
                    resource.getTitle(),
                    firstNonBlank(resource.getTags(), resource.getCategory())));
        }
        return chunks;
    }

    private List<KnowledgeChunk> buildSubjectKnowledgeChunks(SubjectKnowledge subjectKnowledge) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int index = 0;
        for (String content : splitContent(subjectKnowledge.getSummary())) {
            chunks.add(buildChunk(
                    SOURCE_SUBJECT_KNOWLEDGE,
                    subjectKnowledge.getId(),
                    index++,
                    subjectKnowledge.getName(),
                    content,
                    subjectKnowledge.getSubject(),
                    subjectKnowledge.getSourceUrl(),
                    null,
                    subjectKnowledge.getName(),
                    subjectKnowledge.getTag()));
        }
        for (String content : splitContent(subjectKnowledge.getIdeologySummary())) {
            chunks.add(buildChunk(
                    SOURCE_SUBJECT_KNOWLEDGE,
                    subjectKnowledge.getId(),
                    index++,
                    subjectKnowledge.getName(),
                    content,
                    subjectKnowledge.getSubject(),
                    subjectKnowledge.getSourceUrl(),
                    null,
                    subjectKnowledge.getName(),
                    subjectKnowledge.getTag()));
        }
        return chunks;
    }

    private KnowledgeChunk buildChunk(
            String sourceType,
            Long sourceId,
            int chunkIndex,
            String title,
            String content,
            String source,
            String sourceUrl,
            Long courseId,
            String knowledgePointName,
            String ideologyElement) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setSourceType(sourceType);
        chunk.setSourceId(sourceId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setTitle(trimToLength(firstNonBlank(title, "Untitled"), 300));
        chunk.setContent(trimToLength(content, 1200));
        chunk.setSource(trimToLength(safe(source), 200));
        chunk.setSourceUrl(trimToLength(safe(sourceUrl), 500));
        chunk.setCourseId(courseId);
        chunk.setKnowledgePointName(trimToLength(safe(knowledgePointName), 200));
        chunk.setIdeologyElement(trimToLength(safe(ideologyElement), 200));
        chunk.setDeleted(0);
        return chunk;
    }

    private List<String> splitContent(String value) {
        List<String> chunks = new ArrayList<>();
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return chunks;
        }
        if (normalized.length() <= MAX_CHUNK_LENGTH) {
            chunks.add(normalized);
            return chunks;
        }

        String[] paragraphs = normalized.split("\\n+");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String part = paragraph.trim();
            if (part.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + part.length() + 1 > MAX_CHUNK_LENGTH) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (part.length() > MAX_CHUNK_LENGTH) {
                flushLongPart(chunks, part);
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(part);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private void flushLongPart(List<String> chunks, String part) {
        int start = 0;
        while (start < part.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, part.length());
            chunks.add(part.substring(start, end));
            start = end;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return safe(second);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
