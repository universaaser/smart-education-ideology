package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.KnowledgeContextItem;
import com.smartedu.dto.KnowledgeRetrievalResult;
import com.smartedu.entity.IdeologyKnowledge;
import com.smartedu.entity.KnowledgeChunk;
import com.smartedu.entity.Resource;
import com.smartedu.entity.SubjectIdeologyMatch;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.SubjectKnowledgeSource;
import com.smartedu.mapper.IdeologyKnowledgeMapper;
import com.smartedu.mapper.KnowledgeChunkMapper;
import com.smartedu.mapper.ResourceMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.SubjectKnowledgeSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一知识检索服务。
 *
 * <p>
 * 当前实现是轻量 RAG：优先检索 knowledge_chunks，失败或不足时回退到旧的知识点/资源 LIKE 检索，
 * 不引入 embedding 和向量库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    public static final String STATUS_FOUND = "FOUND";
    public static final String STATUS_WEAK_MATCH = "WEAK_MATCH";
    public static final String STATUS_NO_CONTEXT = "NO_CONTEXT";

    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final SubjectKnowledgeSourceMapper subjectKnowledgeSourceMapper;
    private final SubjectIdeologyMatchMapper subjectIdeologyMatchMapper;
    private final IdeologyKnowledgeMapper ideologyKnowledgeMapper;
    private final ResourceMapper resourceMapper;
    private final ResourceService resourceService;

    public List<KnowledgeContextItem> retrieveContext(String query, int knowledgeLimit, int resourceLimit) {
        return retrieveWithStatus(query, knowledgeLimit + resourceLimit).getContexts();
    }

    public KnowledgeRetrievalResult retrieveWithStatus(String query, int limit) {
        int safeLimit = clampLimit(limit, 1, 8);
        Map<String, KnowledgeContextItem> items = new LinkedHashMap<>();

        for (KnowledgeChunk chunk : searchChunksFullText(query, safeLimit)) {
            KnowledgeContextItem item = buildChunkContext(chunk, "FULLTEXT");
            items.putIfAbsent(chunkKey(chunk), item);
        }

        if (items.size() < safeLimit) {
            for (KnowledgeChunk chunk : searchChunksLike(query, safeLimit - items.size())) {
                KnowledgeContextItem item = buildChunkContext(chunk, "LIKE");
                items.putIfAbsent(chunkKey(chunk), item);
            }
        }

        if (!items.isEmpty()) {
            String status = items.values().stream()
                    .anyMatch(item -> "FULLTEXT".equals(item.getMatchedBy()))
                    ? STATUS_FOUND
                    : STATUS_WEAK_MATCH;
            return new KnowledgeRetrievalResult(status, new ArrayList<>(items.values()));
        }

        for (SubjectKnowledge subjectKnowledge : findRelevantSubjectKnowledge(query, Math.min(3, safeLimit))) {
            KnowledgeContextItem item = buildSubjectKnowledgeContext(subjectKnowledge);
            item.setMatchedBy("LEGACY_LIKE");
            item.setSnippet(item.getSummary());
            items.putIfAbsent("SK-" + subjectKnowledge.getId(), item);
        }

        if (resourceService != null) {
            int remaining = Math.max(1, safeLimit - items.size());
            for (Resource resource : resourceService.searchForChatContext(query, Math.min(3, remaining))) {
                KnowledgeContextItem item = new KnowledgeContextItem(
                        "RESOURCE",
                        resource.getId(),
                        safe(resource.getTitle()),
                        firstNonBlank(resource.getIdeologySummary(), resource.getContent()),
                        safe(resource.getSource()),
                        safe(resource.getSourceUrl()),
                        "");
                item.setMatchedBy("LEGACY_LIKE");
                item.setSnippet(truncate(item.getSummary(), 260));
                items.putIfAbsent("RS-" + resource.getId(), item);
            }
        }

        String status = items.isEmpty() ? STATUS_NO_CONTEXT : STATUS_WEAK_MATCH;
        return new KnowledgeRetrievalResult(status, new ArrayList<>(items.values()));
    }

    public List<SubjectKnowledge> findRelevantSubjectKnowledge(String query, int limit) {
        int safeLimit = clampLimit(limit, 1, 10);
        LambdaQueryWrapper<SubjectKnowledge> wrapper = new LambdaQueryWrapper<>();

        List<String> searchTerms = tokenize(query);
        if (!searchTerms.isEmpty()) {
            wrapper.and(group -> {
                boolean firstTerm = true;
                for (String term : searchTerms) {
                    if (!firstTerm) {
                        group.or();
                    }
                    group.and(w -> w.like(SubjectKnowledge::getName, term)
                            .or()
                            .like(SubjectKnowledge::getSummary, term)
                            .or()
                            .like(SubjectKnowledge::getIdeologySummary, term)
                            .or()
                            .like(SubjectKnowledge::getTag, term)
                            .or()
                            .like(SubjectKnowledge::getCategory, term)
                            .or()
                            .like(SubjectKnowledge::getSubject, term));
                    firstTerm = false;
                }
            });
        }

        wrapper.orderByDesc(SubjectKnowledge::getUpdatedAt)
                .orderByDesc(SubjectKnowledge::getCreatedAt)
                .last("LIMIT " + safeLimit);

        return subjectKnowledgeMapper == null ? List.of() : subjectKnowledgeMapper.selectList(wrapper);
    }

    private List<KnowledgeChunk> searchChunksFullText(String query, int limit) {
        if (knowledgeChunkMapper == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            return knowledgeChunkMapper.searchFullText(query, clampLimit(limit, 1, 8));
        } catch (RuntimeException ex) {
            log.warn("Knowledge chunk fulltext retrieval failed, fallback to LIKE: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<KnowledgeChunk> searchChunksLike(String query, int limit) {
        if (knowledgeChunkMapper == null || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        List<String> searchTerms = tokenize(query);
        if (searchTerms.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(group -> {
            boolean firstTerm = true;
            for (String term : searchTerms) {
                if (!firstTerm) {
                    group.or();
                }
                group.and(w -> w.like(KnowledgeChunk::getTitle, term)
                        .or()
                        .like(KnowledgeChunk::getContent, term)
                        .or()
                        .like(KnowledgeChunk::getKnowledgePointName, term)
                        .or()
                        .like(KnowledgeChunk::getIdeologyElement, term)
                        .or()
                        .like(KnowledgeChunk::getSource, term));
                firstTerm = false;
            }
        });
        wrapper.orderByDesc(KnowledgeChunk::getUpdatedAt)
                .last("LIMIT " + clampLimit(limit, 1, 8));
        return knowledgeChunkMapper.selectList(wrapper);
    }

    private KnowledgeContextItem buildChunkContext(KnowledgeChunk chunk, String matchedBy) {
        KnowledgeContextItem item = new KnowledgeContextItem(
                safe(chunk.getSourceType()),
                chunk.getSourceId(),
                safe(chunk.getTitle()),
                safe(chunk.getContent()),
                safe(chunk.getSource()),
                safe(chunk.getSourceUrl()),
                "CHUNK");
        item.setSnippet(truncate(safe(chunk.getContent()), 260));
        item.setMatchedBy(matchedBy);
        item.setScore(chunk.getSearchScore());
        return item;
    }

    private KnowledgeContextItem buildSubjectKnowledgeContext(SubjectKnowledge subjectKnowledge) {
        LambdaQueryWrapper<SubjectKnowledgeSource> sourceWrapper = new LambdaQueryWrapper<>();
        sourceWrapper.eq(SubjectKnowledgeSource::getSubjectKnowledgeId, subjectKnowledge.getId())
                .orderByDesc(SubjectKnowledgeSource::getUpdatedAt)
                .last("LIMIT 1");
        SubjectKnowledgeSource sourceLink = subjectKnowledgeSourceMapper == null ? null : subjectKnowledgeSourceMapper.selectOne(sourceWrapper);

        Resource resource = null;
        if (sourceLink != null && sourceLink.getResourceId() != null && resourceMapper != null) {
            resource = resourceMapper.selectById(sourceLink.getResourceId());
        }

        LambdaQueryWrapper<SubjectIdeologyMatch> matchWrapper = new LambdaQueryWrapper<>();
        matchWrapper.eq(SubjectIdeologyMatch::getSubjectKnowledgeId, subjectKnowledge.getId())
                .orderByDesc(SubjectIdeologyMatch::getIsPrimary)
                .orderByDesc(SubjectIdeologyMatch::getMatchScore)
                .last("LIMIT 1");
        SubjectIdeologyMatch match = subjectIdeologyMatchMapper == null ? null : subjectIdeologyMatchMapper.selectOne(matchWrapper);

        String ideologyTitle = "";
        String ideologyReason = "";
        if (match != null && ideologyKnowledgeMapper != null) {
            IdeologyKnowledge ideology = ideologyKnowledgeMapper.selectById(match.getIdeologyKnowledgeId());
            ideologyTitle = ideology == null ? "" : ideology.getName();
            ideologyReason = match.getMatchReason() == null ? "" : match.getMatchReason();
        }

        String summary = safe(subjectKnowledge.getSummary());
        if (!ideologyTitle.isBlank()) {
            summary = summary + "\nMatched Ideology: " + ideologyTitle;
        }
        if (!ideologyReason.isBlank()) {
            summary = summary + "\nMatch Reason: " + ideologyReason;
        }

        return new KnowledgeContextItem(
                "SUBJECT_KNOWLEDGE",
                subjectKnowledge.getId(),
                safe(subjectKnowledge.getName()),
                summary,
                resource == null ? "" : safe(resource.getSource()),
                sourceLink == null ? safe(subjectKnowledge.getSourceUrl()) : safe(sourceLink.getSourceUrl()),
                "TECH");
    }

    private String chunkKey(KnowledgeChunk chunk) {
        return safe(chunk.getSourceType()) + "-" + chunk.getSourceId() + "-" + chunk.getChunkIndex();
    }

    private int clampLimit(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<String> tokenize(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return terms;
        }

        for (String part : query.split("[\\s,，。；;、/]+")) {
            String term = part.trim();
            if (term.length() >= 2 && !terms.contains(term)) {
                terms.add(term);
            }
            if (terms.size() >= 5) {
                break;
            }
        }
        return terms;
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

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }
}
