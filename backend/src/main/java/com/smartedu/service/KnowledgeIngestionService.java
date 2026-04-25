package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.DocumentStructureDto;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.entity.IdeologyKnowledge;
import com.smartedu.entity.KnowledgeRelation;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.Resource;
import com.smartedu.entity.SubjectIdeologyMatch;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.SubjectKnowledgeSource;
import com.smartedu.mapper.IdeologyKnowledgeMapper;
import com.smartedu.mapper.KnowledgeRelationMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.SubjectKnowledgeSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一知识入库服务
 *
 * <p>
 * 将爬虫资源和上传解析结果统一转换为知识点、关系和来源追溯记录。
 * 这里使用轻量规则抽取，保证在没有额外模型切换和复杂工作流时也能稳定落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    private static final String SOURCE_TYPE_CRAWLED = "CRAWLED_RESOURCE";
    private static final String SOURCE_TYPE_UPLOADED = "UPLOADED_DOCUMENT";
    private static final double GRAPH_GRID_X = 240D;
    private static final double GRAPH_GRID_Y = 180D;
    private static final double NODE_LABEL_LINE_HEIGHT = 16D;

    private final ResourceService resourceService;
    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final IdeologyKnowledgeMapper ideologyKnowledgeMapper;
    private final SubjectIdeologyMatchMapper subjectIdeologyMatchMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final SubjectKnowledgeSourceMapper subjectKnowledgeSourceMapper;
    private final ObjectMapper objectMapper;
    private final KnowledgeChunkService knowledgeChunkService;

    /**
     * 将已有资源转换为知识点和来源信息。
     */
    @Transactional
    public void ingestResource(Resource resource) {
        if (resource == null || resource.getId() == null) {
            return;
        }

        // Normalize crawler output and uploaded document output into one runtime write path.
        SubjectKnowledge subjectKnowledge = upsertSubjectKnowledge(resource);
        createSourceLink(subjectKnowledge, resource);

        List<IdeologyKnowledge> matchedIdeologies = resolveMatchedIdeologies(resource);
        createIdeologyMatches(subjectKnowledge, matchedIdeologies, resource.getIdeologySummary());
        createSubjectRelations(subjectKnowledge, resource);
    }
    /**
     * 上传任务完成后先同步成资源，再复用统一入库逻辑。
     */
    @Transactional
    public Resource ingestParseTask(ParseTask task) {
        if (task == null || task.getId() == null) {
            return null;
        }

        Resource resource = upsertResourceFromParseTask(task);
        ingestResource(resource);
        return resource;
    }

    private SubjectKnowledge upsertSubjectKnowledge(Resource resource) {
        String name = truncate(extractSubjectName(resource), 200);
        String summary = truncate(firstNonBlank(resource.getContent(), resource.getIdeologySummary()), 1000);
        SubjectKnowledge subjectKnowledge = findSubjectKnowledge(name);

        if (subjectKnowledge == null) {
            subjectKnowledge = new SubjectKnowledge();
            subjectKnowledge.setName(name);
            subjectKnowledge.setCreatedAt(LocalDateTime.now());
            applyDefaultPosition(subjectKnowledge);
            subjectKnowledge.setNodeSize("MD");
        }

        // For crawled current-affairs data, resource.content already stores the LLM condensed
        // fragment instead of the raw article, so the subject summary remains short and reusable.
        subjectKnowledge.setSubject(firstNonBlank(resource.getCategory(), resource.getSource()));
        subjectKnowledge.setCategory(firstNonBlank(resource.getCategory(), "General"));
        subjectKnowledge.setSummary(summary);
        subjectKnowledge.setIdeologySummary(truncate(safe(resource.getIdeologySummary()), 1000));
        subjectKnowledge.setTag(extractPrimaryTag(resource));
        subjectKnowledge.setSubTitle(firstNonBlank(resource.getSource(), resource.getCategory()));
        subjectKnowledge.setSourceUrl(resource.getSourceUrl());
        subjectKnowledge.setUpdatedAt(LocalDateTime.now());

        if (subjectKnowledge.getId() == null) {
            subjectKnowledgeMapper.insert(subjectKnowledge);
        } else {
            subjectKnowledgeMapper.updateById(subjectKnowledge);
        }
        knowledgeChunkService.refreshSubjectKnowledgeChunks(subjectKnowledge);
        return subjectKnowledge;
    }

    private Resource upsertResourceFromParseTask(ParseTask task) {
        String syntheticSourceUrl = "upload://parse-task/" + task.getId();
        Resource existing = resourceService.getBySourceUrl(syntheticSourceUrl);
        PipelineResultDto pipelineResult = parsePipelineResult(task.getAiAnalysis());
        DocumentStructureDto documentStructure = resolveDocumentStructure(pipelineResult, task.getParsedContent());
        String resourceContent = buildResourceContent(documentStructure, pipelineResult);
        String ideologySummary = buildIdeologySummary(pipelineResult);
        String tags = buildTagsFromTask(task, pipelineResult);

        if (existing == null) {
            Resource resource = new Resource();
            resource.setTitle(task.getFileName());
            resource.setSource("Uploaded Document");
            resource.setSourceUrl(syntheticSourceUrl);
            resource.setCategory("Document");
            resource.setContent(truncate(resourceContent, 1500));
            resource.setIdeologySummary(truncate(ideologySummary, 1200));
            resource.setTags(tags);
            resource.setFilePath(task.getFilePath());
            resource.setFileType(extractFileType(task.getFileName()));
            resource.setFileSize(task.getFileSize());
            resource.setSyncStatus("SYNCED");
            resource.setParseTaskId(task.getId());
            resource.setCreatorId(task.getUserId());
            return resourceService.createResource(resource);
        }

        existing.setTitle(task.getFileName());
        existing.setSource("Uploaded Document");
        existing.setCategory("Document");
        existing.setContent(truncate(resourceContent, 1500));
        existing.setIdeologySummary(truncate(ideologySummary, 1200));
        existing.setTags(tags);
        existing.setFilePath(task.getFilePath());
        existing.setFileType(extractFileType(task.getFileName()));
        existing.setFileSize(task.getFileSize());
        existing.setSyncStatus("SYNCED");
        existing.setParseTaskId(task.getId());
        existing.setCreatorId(task.getUserId());
        return resourceService.updateResource(existing);
    }

    private void createSourceLink(SubjectKnowledge subjectKnowledge, Resource resource) {
        if (subjectKnowledge == null || subjectKnowledge.getId() == null || resource == null || resource.getId() == null) {
            return;
        }

        LambdaQueryWrapper<SubjectKnowledgeSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectKnowledgeSource::getSubjectKnowledgeId, subjectKnowledge.getId())
                .eq(SubjectKnowledgeSource::getResourceId, resource.getId())
                .last("LIMIT 1");
        SubjectKnowledgeSource existing = subjectKnowledgeSourceMapper.selectOne(wrapper);

        if (existing == null) {
            existing = new SubjectKnowledgeSource();
            existing.setSubjectKnowledgeId(subjectKnowledge.getId());
            existing.setResourceId(resource.getId());
            existing.setCreatedAt(LocalDateTime.now());
        }

        existing.setSourceType(resource.getParseTaskId() == null ? SOURCE_TYPE_CRAWLED : SOURCE_TYPE_UPLOADED);
        existing.setSourceUrl(resource.getSourceUrl());
        existing.setExcerpt(truncate(firstNonBlank(resource.getIdeologySummary(), resource.getContent()), 500));
        existing.setUpdatedAt(LocalDateTime.now());

        if (existing.getId() == null) {
            subjectKnowledgeSourceMapper.insert(existing);
        } else {
            subjectKnowledgeSourceMapper.updateById(existing);
        }
    }

    private void createSubjectRelations(SubjectKnowledge subjectKnowledge, Resource resource) {
        if (subjectKnowledge == null || subjectKnowledge.getId() == null) {
            return;
        }

        List<SubjectKnowledge> relatedSubjects = resourceService.searchForChatContext(subjectKnowledge.getName(), 3).stream()
                .filter(item -> item.getId() != null && !item.getId().equals(resource.getId()))
                .map(this::findSubjectKnowledgeByResource)
                .filter(item -> item != null && !item.getId().equals(subjectKnowledge.getId()))
                .toList();

        for (SubjectKnowledge relatedSubject : relatedSubjects) {
            createOrUpdateSubjectRelation(subjectKnowledge.getId(), relatedSubject.getId());
        }
    }

    private void createOrUpdateSubjectRelation(Long fromNodeId, Long toNodeId) {
        if (fromNodeId == null || toNodeId == null || fromNodeId.equals(toNodeId)) {
            return;
        }

        LambdaQueryWrapper<KnowledgeRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeRelation::getFromNodeId, fromNodeId)
                .eq(KnowledgeRelation::getToNodeId, toNodeId)
                .last("LIMIT 1");
        KnowledgeRelation relation = knowledgeRelationMapper.selectOne(wrapper);

        if (relation == null) {
            relation = new KnowledgeRelation();
            relation.setFromNodeId(fromNodeId);
            relation.setToNodeId(toNodeId);
            relation.setCreatedAt(LocalDateTime.now());
            relation.setWeight(1.0);
            relation.setRelationType("THEORY_SUPPORT");
            relation.setLineStyle("SOLID");
            relation.setDescription("Auto-linked subject knowledge relation.");
            relation.setUpdatedAt(LocalDateTime.now());
            knowledgeRelationMapper.insert(relation);
        } else {
            relation.setUpdatedAt(LocalDateTime.now());
            knowledgeRelationMapper.updateById(relation);
        }
    }

    private void createIdeologyMatches(SubjectKnowledge subjectKnowledge, List<IdeologyKnowledge> ideologies, String matchReason) {
        if (subjectKnowledge == null || subjectKnowledge.getId() == null) {
            return;
        }

        LambdaQueryWrapper<SubjectIdeologyMatch> subjectWrapper = new LambdaQueryWrapper<>();
        subjectWrapper.eq(SubjectIdeologyMatch::getSubjectKnowledgeId, subjectKnowledge.getId());
        if (ideologies.isEmpty()) {
            subjectIdeologyMatchMapper.delete(subjectWrapper);
            return;
        }

        List<Long> ideologyIds = ideologies.stream()
                .map(IdeologyKnowledge::getId)
                .toList();
        LambdaQueryWrapper<SubjectIdeologyMatch> staleWrapper = new LambdaQueryWrapper<>();
        staleWrapper.eq(SubjectIdeologyMatch::getSubjectKnowledgeId, subjectKnowledge.getId())
                .notIn(SubjectIdeologyMatch::getIdeologyKnowledgeId, ideologyIds);
        subjectIdeologyMatchMapper.delete(staleWrapper);

        // Only fixed ideology dictionary entries are persisted here. Free-form model output
        // must be resolved to ideology_knowledge first, which keeps category vocabulary stable.
        int index = 0;
        for (IdeologyKnowledge ideology : ideologies) {
            LambdaQueryWrapper<SubjectIdeologyMatch> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SubjectIdeologyMatch::getSubjectKnowledgeId, subjectKnowledge.getId())
                    .eq(SubjectIdeologyMatch::getIdeologyKnowledgeId, ideology.getId())
                    .last("LIMIT 1");
            SubjectIdeologyMatch match = subjectIdeologyMatchMapper.selectOne(wrapper);
            if (match == null) {
                match = new SubjectIdeologyMatch();
                match.setSubjectKnowledgeId(subjectKnowledge.getId());
                match.setIdeologyKnowledgeId(ideology.getId());
                match.setCreatedAt(LocalDateTime.now());
            }

            match.setIsPrimary(index == 0 ? 1 : 0);
            match.setMatchScore(BigDecimal.valueOf(index == 0 ? 95.00 : 80.00));
            match.setMatchReason(truncate(firstNonBlank(matchReason, ideology.getDescription()), 1000));
            match.setReviewStatus("APPROVED");
            if (match.getId() == null) {
                subjectIdeologyMatchMapper.insert(match);
            } else {
                subjectIdeologyMatchMapper.updateById(match);
            }
            index++;
        }
    }

    private SubjectKnowledge findSubjectKnowledge(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        LambdaQueryWrapper<SubjectKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectKnowledge::getName, name)
                .last("LIMIT 1");
        return subjectKnowledgeMapper.selectOne(wrapper);
    }

    private SubjectKnowledge findSubjectKnowledgeByResource(Resource resource) {
        if (resource == null) {
            return null;
        }
        LambdaQueryWrapper<SubjectKnowledgeSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectKnowledgeSource::getResourceId, resource.getId()).last("LIMIT 1");
        SubjectKnowledgeSource source = subjectKnowledgeSourceMapper.selectOne(wrapper);
        if (source == null) {
            return null;
        }
        return subjectKnowledgeMapper.selectById(source.getSubjectKnowledgeId());
    }

    private List<IdeologyKnowledge> resolveMatchedIdeologies(Resource resource) {
        List<IdeologyKnowledge> result = new ArrayList<>();
        List<String> categories = parseTags(resource.getTags());
        List<IdeologyKnowledge> allIdeologies = ideologyKnowledgeMapper.selectList(new LambdaQueryWrapper<>());

        for (String category : categories) {
            IdeologyKnowledge matched = findIdeologyByNameOrKeyword(allIdeologies, category);
            if (matched != null && result.stream().noneMatch(item -> item.getId().equals(matched.getId()))) {
                result.add(matched);
            }
        }

        if (false && result.isEmpty()) {
            IdeologyKnowledge fallback = findIdeologyByReason(allIdeologies, resource.getIdeologySummary());
            if (fallback != null) {
                result.add(fallback);
            }
        }

        if (false && result.isEmpty()) {
            LambdaQueryWrapper<IdeologyKnowledge> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(IdeologyKnowledge::getName, "工匠精神").last("LIMIT 1");
            IdeologyKnowledge fallback = ideologyKnowledgeMapper.selectOne(wrapper);
            if (fallback != null) {
                result.add(fallback);
            }
        }
        return result;
    }

    private IdeologyKnowledge findIdeologyByNameOrKeyword(List<IdeologyKnowledge> ideologies, String text) {
        String normalizedText = safe(text).toLowerCase();
        if (normalizedText.isBlank()) {
            return null;
        }
        for (IdeologyKnowledge ideology : ideologies) {
            if (safe(ideology.getName()).toLowerCase().contains(normalizedText)
                    || normalizedText.contains(safe(ideology.getName()).toLowerCase())) {
                return ideology;
            }
            for (String keyword : safe(ideology.getKeywords()).split("[,，]")) {
                String trimmed = keyword.trim().toLowerCase();
                if (!trimmed.isBlank() && normalizedText.contains(trimmed)) {
                    return ideology;
                }
            }
        }
        return null;
    }

    private IdeologyKnowledge findIdeologyByReason(List<IdeologyKnowledge> ideologies, String text) {
        String normalizedText = safe(text).toLowerCase();
        for (IdeologyKnowledge ideology : ideologies) {
            for (String keyword : safe(ideology.getKeywords()).split("[,，]")) {
                String trimmed = keyword.trim().toLowerCase();
                if (!trimmed.isBlank() && normalizedText.contains(trimmed)) {
                    return ideology;
                }
            }
        }
        return ideologies.isEmpty() ? null : ideologies.get(0);
    }

    private void applyDefaultPosition(SubjectKnowledge subjectKnowledge) {
        List<SubjectKnowledge> existingNodes = subjectKnowledgeMapper.selectList(new LambdaQueryWrapper<>());
        for (int layer = 0; layer <= 12; layer++) {
            for (GraphPosition candidate : buildCandidatePositions(layer)) {
                if (!hasNodeOverlap(candidate, subjectKnowledge.getName(), subjectKnowledge.getNodeSize(), existingNodes)) {
                    subjectKnowledge.setPositionX(candidate.x);
                    subjectKnowledge.setPositionY(candidate.y);
                    return;
                }
            }
        }

        double fallbackY = existingNodes.stream()
                .map(SubjectKnowledge::getPositionY)
                .filter(value -> value != null)
                .max(Double::compareTo)
                .orElse(0D) + GRAPH_GRID_Y;
        subjectKnowledge.setPositionX(0D);
        subjectKnowledge.setPositionY(fallbackY);
    }

    private List<GraphPosition> buildCandidatePositions(int layer) {
        List<GraphPosition> candidates = new ArrayList<>();
        if (layer == 0) {
            candidates.add(new GraphPosition(0D, 0D));
            return candidates;
        }

        for (int x = -layer; x <= layer; x++) {
            candidates.add(new GraphPosition(x * GRAPH_GRID_X, -layer * GRAPH_GRID_Y));
            candidates.add(new GraphPosition(x * GRAPH_GRID_X, layer * GRAPH_GRID_Y));
        }
        for (int y = -layer + 1; y <= layer - 1; y++) {
            candidates.add(new GraphPosition(-layer * GRAPH_GRID_X, y * GRAPH_GRID_Y));
            candidates.add(new GraphPosition(layer * GRAPH_GRID_X, y * GRAPH_GRID_Y));
        }
        return candidates;
    }

    private boolean hasNodeOverlap(
            GraphPosition candidate,
            String candidateName,
            String candidateNodeSize,
            List<SubjectKnowledge> existingNodes) {
        double candidateWidth = estimateNodeFootprintWidth(candidateName, candidateNodeSize);
        double candidateHeight = estimateNodeFootprintHeight(candidateName, candidateNodeSize);
        for (SubjectKnowledge existingNode : existingNodes) {
            double existingWidth = estimateNodeFootprintWidth(existingNode.getName(), existingNode.getNodeSize());
            double existingHeight = estimateNodeFootprintHeight(existingNode.getName(), existingNode.getNodeSize());
            double existingX = existingNode.getPositionX() == null ? 0D : existingNode.getPositionX();
            double existingY = existingNode.getPositionY() == null ? 0D : existingNode.getPositionY();
            boolean xOverlap = Math.abs(candidate.x - existingX) < ((candidateWidth + existingWidth) / 2D);
            boolean yOverlap = Math.abs(candidate.y - existingY) < ((candidateHeight + existingHeight) / 2D);
            if (xOverlap && yOverlap) {
                return true;
            }
        }
        return false;
    }

    private double estimateNodeFootprintWidth(String nodeName, String nodeSize) {
        double nodePixelSize = resolveNodePixelSize(nodeSize);
        double labelWidth = Math.max(90D, resolveLabelMaxChars(nodeSize) * 16D);
        return Math.max(nodePixelSize + 48D, labelWidth);
    }

    private double estimateNodeFootprintHeight(String nodeName, String nodeSize) {
        double nodePixelSize = resolveNodePixelSize(nodeSize);
        int lineCount = Math.max(1, estimateLineCount(nodeName, nodeSize));
        return nodePixelSize + 48D + (lineCount * NODE_LABEL_LINE_HEIGHT);
    }

    private int estimateLineCount(String nodeName, String nodeSize) {
        String normalizedName = nodeName == null ? "" : nodeName.trim();
        if (normalizedName.isBlank()) {
            return 1;
        }
        int charsPerLine = resolveLabelMaxChars(nodeSize);
        return (int) Math.ceil((double) normalizedName.length() / charsPerLine);
    }

    private int resolveLabelMaxChars(String nodeSize) {
        return switch (normalizeNodeSize(nodeSize)) {
            case "SM" -> 6;
            case "LG" -> 10;
            default -> 8;
        };
    }

    private double resolveNodePixelSize(String nodeSize) {
        return switch (normalizeNodeSize(nodeSize)) {
            case "SM" -> 96D;
            case "LG" -> 144D;
            default -> 120D;
        };
    }

    private String normalizeNodeSize(String nodeSize) {
        if (nodeSize == null || nodeSize.isBlank()) {
            return "MD";
        }
        String normalized = nodeSize.trim().toUpperCase();
        if ("SM".equals(normalized) || "LG".equals(normalized)) {
            return normalized;
        }
        return "MD";
    }

    private static final class GraphPosition {
        private final double x;
        private final double y;

        private GraphPosition(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private String extractSubjectName(Resource resource) {
        String title = safe(resource.getTitle());
        if (!title.isBlank()) {
            return title;
        }
        return "Imported Technical Topic";
    }

    private String extractPrimaryTag(Resource resource) {
        List<String> tags = parseTags(resource.getTags());
        if (!tags.isEmpty()) {
            return tags.get(0);
        }
        return "";
    }

    private List<String> parseTags(String tagsJson) {
        List<String> result = new ArrayList<>();
        if (tagsJson == null || tagsJson.isBlank()) {
            return result;
        }

        try {
            List<String> tags = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
            });
            for (String tag : tags) {
                if (tag != null && !tag.isBlank()) {
                    result.add(tag.trim());
                }
            }
            return result;
        } catch (Exception ex) {
            log.debug("parse tags fallback used: {}", ex.getMessage());
        }

        for (String tag : tagsJson.replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .split("[,，;；]")) {
            if (!tag.isBlank()) {
                result.add(tag.trim());
            }
        }
        return result;
    }

    private String buildTagsFromTask(ParseTask task, PipelineResultDto pipelineResult) {
        List<String> tags = new ArrayList<>();
        tags.add("uploaded");
        tags.add("document");

        String fileName = safe(task.getFileName()).toLowerCase();
        if (fileName.endsWith(".pdf")) {
            tags.add("pdf");
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            tags.add("word");
        } else if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) {
            tags.add("slide");
        } else if (fileName.endsWith(".md") || fileName.endsWith(".markdown")) {
            tags.add("markdown");
        }

        if (pipelineResult != null && pipelineResult.getIdeologyMatches() != null) {
            for (IdeologyMatchDto match : pipelineResult.getIdeologyMatches()) {
                String ideologyElement = safe(match.getIdeologyElement());
                if (!ideologyElement.isBlank() && !tags.contains(ideologyElement)) {
                    tags.add(ideologyElement);
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception ex) {
            return "[\"uploaded\",\"document\"]";
        }
    }

    private PipelineResultDto parsePipelineResult(String aiAnalysisJson) {
        if (aiAnalysisJson == null || aiAnalysisJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(aiAnalysisJson, PipelineResultDto.class);
        } catch (Exception ex) {
            log.warn("Failed to parse pipeline result from aiAnalysis, fallback to raw text: {}", ex.getMessage());
            return null;
        }
    }

    private DocumentStructureDto resolveDocumentStructure(PipelineResultDto pipelineResult, String parsedContentJson) {
        if (pipelineResult != null && pipelineResult.getDocumentStructure() != null) {
            return pipelineResult.getDocumentStructure();
        }
        if (parsedContentJson == null || parsedContentJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(parsedContentJson, DocumentStructureDto.class);
        } catch (Exception ex) {
            log.warn("Failed to parse document structure from parsedContent, fallback to raw text: {}", ex.getMessage());
            return null;
        }
    }

    private String buildResourceContent(DocumentStructureDto documentStructure, PipelineResultDto pipelineResult) {
        List<String> lines = new ArrayList<>();
        if (documentStructure != null) {
            lines.add("Overview: " + safe(documentStructure.getOverview()));
            if (documentStructure.getChapterOutline() != null && !documentStructure.getChapterOutline().isEmpty()) {
                lines.add("Chapters: " + String.join(" | ", documentStructure.getChapterOutline()));
            }
        }

        if (pipelineResult != null && pipelineResult.getKnowledgePoints() != null) {
            for (KnowledgePointDto point : pipelineResult.getKnowledgePoints()) {
                lines.add("- " + safe(point.getPointName()) + ": " + safe(point.getDefinition()));
            }
        }

        String combined = String.join("\n", lines).trim();
        if (!combined.isBlank()) {
            return combined;
        }

        if (documentStructure != null) {
            return safe(documentStructure.getOverview());
        }

        return "";
    }

    private String buildIdeologySummary(PipelineResultDto pipelineResult) {
        if (pipelineResult == null || pipelineResult.getIdeologyMatches() == null || pipelineResult.getIdeologyMatches().isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        for (IdeologyMatchDto match : pipelineResult.getIdeologyMatches()) {
            lines.add(safe(match.getKnowledgePointName()) + " -> " + safe(match.getIdeologyElement()) + ": " + safe(match.getMatchReason()));
        }
        return String.join("\n", lines);
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return safe(second);
    }

    private String truncate(String text, int maxLength) {
        String value = safe(text);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
