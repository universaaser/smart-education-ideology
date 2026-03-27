package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.KnowledgeContextItem;
import com.smartedu.entity.IdeologyKnowledge;
import com.smartedu.entity.Resource;
import com.smartedu.entity.SubjectIdeologyMatch;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.SubjectKnowledgeSource;
import com.smartedu.mapper.IdeologyKnowledgeMapper;
import com.smartedu.mapper.ResourceMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.SubjectKnowledgeSourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一知识检索服务
 *
 * <p>
 * 将知识点检索和资源检索收敛到同一入口，避免聊天、解释、推荐各自维护不同的查询逻辑。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final SubjectKnowledgeSourceMapper subjectKnowledgeSourceMapper;
    private final SubjectIdeologyMatchMapper subjectIdeologyMatchMapper;
    private final IdeologyKnowledgeMapper ideologyKnowledgeMapper;
    private final ResourceMapper resourceMapper;
    private final ResourceService resourceService;

    /**
     * 返回混合上下文，优先包含知识点，再补充原始资源，供聊天或解释接口直接拼接提示词。
     */
    public List<KnowledgeContextItem> retrieveContext(String query, int knowledgeLimit, int resourceLimit) {
        int safeKnowledgeLimit = clampLimit(knowledgeLimit, 1, 6);
        int safeResourceLimit = clampLimit(resourceLimit, 1, 6);

        Map<String, KnowledgeContextItem> items = new LinkedHashMap<>();

        for (SubjectKnowledge subjectKnowledge : findRelevantSubjectKnowledge(query, safeKnowledgeLimit)) {
            KnowledgeContextItem item = buildSubjectKnowledgeContext(subjectKnowledge);
            items.putIfAbsent("SK-" + subjectKnowledge.getId(), item);
        }

        for (Resource resource : resourceService.searchForChatContext(query, safeResourceLimit)) {
            KnowledgeContextItem item = new KnowledgeContextItem(
                    "RESOURCE",
                    resource.getId(),
                    safe(resource.getTitle()),
                    firstNonBlank(resource.getIdeologySummary(), resource.getContent()),
                    safe(resource.getSource()),
                    safe(resource.getSourceUrl()),
                    "");
            items.putIfAbsent("RS-" + resource.getId(), item);
        }

        return new ArrayList<>(items.values());
    }

    /**
     * 提供给课程自动关联和未来解释接口使用的知识点检索入口。
     */
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

        return subjectKnowledgeMapper.selectList(wrapper);
    }

    private KnowledgeContextItem buildSubjectKnowledgeContext(SubjectKnowledge subjectKnowledge) {
        // Prefer the newest source trace so downstream prompts always receive a stable
        // and explainable evidence link for the current subject knowledge record.
        LambdaQueryWrapper<SubjectKnowledgeSource> sourceWrapper = new LambdaQueryWrapper<>();
        sourceWrapper.eq(SubjectKnowledgeSource::getSubjectKnowledgeId, subjectKnowledge.getId())
                .orderByDesc(SubjectKnowledgeSource::getUpdatedAt)
                .last("LIMIT 1");
        SubjectKnowledgeSource sourceLink = subjectKnowledgeSourceMapper.selectOne(sourceWrapper);

        Resource resource = null;
        if (sourceLink != null && sourceLink.getResourceId() != null) {
            resource = resourceMapper.selectById(sourceLink.getResourceId());
        }

        LambdaQueryWrapper<SubjectIdeologyMatch> matchWrapper = new LambdaQueryWrapper<>();
        matchWrapper.eq(SubjectIdeologyMatch::getSubjectKnowledgeId, subjectKnowledge.getId())
                .orderByDesc(SubjectIdeologyMatch::getIsPrimary)
                .orderByDesc(SubjectIdeologyMatch::getMatchScore)
                .last("LIMIT 1");
        SubjectIdeologyMatch match = subjectIdeologyMatchMapper.selectOne(matchWrapper);

        String ideologyTitle = "";
        String ideologyReason = "";
        if (match != null) {
            IdeologyKnowledge ideology = ideologyKnowledgeMapper.selectById(match.getIdeologyKnowledgeId());
            ideologyTitle = ideology == null ? "" : ideology.getName();
            ideologyReason = match.getMatchReason() == null ? "" : match.getMatchReason();
        }

        // Assemble a compact retrieval summary once here so chat and explain-selection can
        // share the same context format without duplicating cross-table join logic.
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

    private int clampLimit(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 将长文本拆成少量短语，提升知识点和资源命中率，同时控制 SQL 复杂度。
     */
    private List<String> tokenize(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return terms;
        }

        // Keep the token count intentionally small so the generated SQL stays predictable.
        for (String part : query.split("[\\s,，。；;：:、|/]+")) {
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
}
