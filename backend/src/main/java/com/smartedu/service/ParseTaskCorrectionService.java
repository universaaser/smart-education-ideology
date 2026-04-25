package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.DocumentStructureDto;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.ParseTaskCorrectionDraftDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.ResourceCitationDto;
import com.smartedu.dto.TeachingArtifactsDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.ParseTaskCorrection;
import com.smartedu.entity.ParseTaskIdeologyMatch;
import com.smartedu.entity.ParseTaskKnowledgePoint;
import com.smartedu.mapper.ParseTaskCorrectionMapper;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.mapper.ParseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Parse task manual correction service.
 *
 * <p>
 * This service stores the latest corrected structured result, resolves the
 * effective pipeline output for downstream teaching material generation, and
 * keeps projection/vector indexes aligned with the corrected snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseTaskCorrectionService {

    private static final String SOURCE_PIPELINE = "PIPELINE";
    private static final String SOURCE_CORRECTION = "CORRECTION";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_STALE = "STALE";

    private final ParseTaskCorrectionMapper parseTaskCorrectionMapper;
    private final ParseTaskMapper parseTaskMapper;
    private final ParseTaskKnowledgePointMapper parseTaskKnowledgePointMapper;
    private final ParseTaskIdeologyMatchMapper parseTaskIdeologyMatchMapper;
    private final AiIntelligenceService aiIntelligenceService;
    private final AiPipelineJsonValidator aiPipelineJsonValidator;
    private final VectorIndexAsyncService vectorIndexAsyncService;
    private final ObjectMapper objectMapper;

    @Value("${ai.pipeline.max-knowledge-points:12}")
    private int maxKnowledgePoints;

    @Value("${ai.pipeline.max-ideology-matches-per-point:3}")
    private int maxIdeologyMatchesPerPoint;

    @Value("${ai.pipeline.max-questions:6}")
    private int maxQuestions;

    @Value("${ai.pipeline.schema-version:v1}")
    private String schemaVersion;

    /**
     * Load the current editable correction draft for a completed parse task.
     */
    public ParseTaskCorrectionDraftDto getCorrectionDraft(Long taskId) {
        ParseTask task = requireCompletedTask(taskId);
        PipelineResultDto pipelineResult = aiIntelligenceService.getPipelineResult(taskId);
        ParseTaskCorrection correction = findByTaskId(taskId);
        if (isUsableCorrection(correction, task)) {
            PipelineResultDto correctedResult = readPipelineResult(correction.getCorrectedResultJson());
            return buildDraftDto(SOURCE_CORRECTION, false, correction, correctedResult);
        }

        return buildDraftDto(SOURCE_PIPELINE, correction != null, correction, pipelineResult);
    }

    /**
     * Save the latest correction draft and refresh projection/vector indexes.
     */
    @Transactional
    public ParseTaskCorrectionDraftDto saveCorrectionDraft(Long taskId, PipelineResultDto request) {
        ParseTask task = requireCompletedTask(taskId);
        if (task.getCompletedAt() == null) {
            throw new RuntimeException("Completed task is missing completedAt");
        }

        PipelineResultDto normalized = validateAndNormalize(request);
        ParseTaskCorrection correction = findByTaskId(taskId);
        LocalDateTime now = LocalDateTime.now();
        if (correction == null) {
            correction = new ParseTaskCorrection();
            correction.setParseTaskId(taskId);
            correction.setCreatedAt(now);
        }

        correction.setCorrectedResultJson(writeJsonSafely(normalized));
        correction.setSourceCompletedAt(task.getCompletedAt());
        correction.setStatus(STATUS_ACTIVE);
        correction.setUpdatedAt(now);
        if (correction.getId() == null) {
            parseTaskCorrectionMapper.insert(correction);
        } else {
            parseTaskCorrectionMapper.updateById(correction);
        }

        syncPipelineProjections(task, normalized);
        vectorIndexAsyncService.indexPipelineResult(task, normalized);
        return buildDraftDto(SOURCE_CORRECTION, false, correction, normalized);
    }

    /**
     * Mark an existing correction snapshot as stale before a new parse run.
     */
    public void markCorrectionStale(Long taskId) {
        ParseTaskCorrection correction = findByTaskId(taskId);
        if (correction == null || STATUS_STALE.equalsIgnoreCase(safe(correction.getStatus()))) {
            return;
        }
        correction.setStatus(STATUS_STALE);
        correction.setUpdatedAt(LocalDateTime.now());
        parseTaskCorrectionMapper.updateById(correction);
    }

    /**
     * Resolve the effective structured result for downstream teaching material
     * draft/version generation.
     */
    public PipelineResultDto getEffectivePipelineResult(Long taskId) {
        ParseTask task = requireParseTask(taskId);
        PipelineResultDto fallback = aiIntelligenceService.getPipelineResult(taskId);
        return resolveEffectivePipelineResult(task, fallback);
    }

    /**
     * Resolve the effective structured result with a preloaded fallback.
     */
    public PipelineResultDto resolveEffectivePipelineResult(ParseTask task, PipelineResultDto fallback) {
        if (task == null || task.getId() == null) {
            return fallback;
        }

        ParseTaskCorrection correction = findByTaskId(task.getId());
        if (!isUsableCorrection(correction, task)) {
            return fallback;
        }

        try {
            return readPipelineResult(correction.getCorrectedResultJson());
        } catch (RuntimeException ex) {
            log.warn("Failed to deserialize active correction for taskId={}, fallback to pipeline result", task.getId(), ex);
            return fallback;
        }
    }

    private ParseTaskCorrectionDraftDto buildDraftDto(
            String source,
            boolean stale,
            ParseTaskCorrection correction,
            PipelineResultDto result) {
        ParseTaskCorrectionDraftDto dto = new ParseTaskCorrectionDraftDto();
        dto.setSource(source);
        dto.setStale(stale);
        dto.setSavedAt(correction == null ? null : correction.getUpdatedAt());
        dto.setSourceCompletedAt(correction == null ? null : correction.getSourceCompletedAt());
        dto.setResult(result);
        return dto;
    }

    private boolean isUsableCorrection(ParseTaskCorrection correction, ParseTask task) {
        if (correction == null || task == null) {
            return false;
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(safe(correction.getStatus()))) {
            return false;
        }
        if (task.getCompletedAt() == null || correction.getSourceCompletedAt() == null) {
            return false;
        }
        return Objects.equals(task.getCompletedAt(), correction.getSourceCompletedAt());
    }

    private ParseTask requireParseTask(Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("Parse task not found");
        }
        return task;
    }

    private ParseTask requireCompletedTask(Long taskId) {
        ParseTask task = requireParseTask(taskId);
        if (!"COMPLETED".equalsIgnoreCase(safe(task.getStatus()))) {
            throw new RuntimeException("Only completed parse tasks support correction");
        }
        return task;
    }

    private ParseTaskCorrection findByTaskId(Long taskId) {
        LambdaQueryWrapper<ParseTaskCorrection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParseTaskCorrection::getParseTaskId, taskId).last("LIMIT 1");
        return parseTaskCorrectionMapper.selectOne(wrapper);
    }

    private PipelineResultDto readPipelineResult(String json) {
        try {
            PipelineResultDto result = objectMapper.readValue(json, PipelineResultDto.class);
            if (result.getKnowledgePoints() == null) {
                result.setKnowledgePoints(new ArrayList<>());
            }
            if (result.getIdeologyMatches() == null) {
                result.setIdeologyMatches(new ArrayList<>());
            }
            if (result.getWarnings() == null) {
                result.setWarnings(new ArrayList<>());
            }
            if (safe(result.getSchemaVersion()).isBlank()) {
                result.setSchemaVersion(schemaVersion);
            }
            return result;
        } catch (Exception ex) {
            throw new RuntimeException("Correction result JSON is invalid", ex);
        }
    }

    private PipelineResultDto validateAndNormalize(PipelineResultDto request) {
        if (request == null) {
            throw new RuntimeException("Correction result cannot be empty");
        }

        PipelineResultDto normalized = new PipelineResultDto();
        normalized.setDocumentStructure(validateDocumentStructure(request.getDocumentStructure()));
        normalized.setKnowledgePoints(validateKnowledgePoints(request.getKnowledgePoints()));
        normalized.setIdeologyMatches(validateIdeologyMatches(request.getIdeologyMatches(), normalized.getKnowledgePoints()));
        normalized.setTeachingArtifacts(validateTeachingArtifacts(request.getTeachingArtifacts()));
        normalized.setWarnings(new ArrayList<>());
        normalized.setInferred(false);
        normalized.setSchemaVersion(schemaVersion);
        return normalized;
    }

    private DocumentStructureDto validateDocumentStructure(DocumentStructureDto structure) {
        if (structure == null) {
            throw new RuntimeException("Document structure cannot be empty");
        }
        // rawMarkdown / parseMode / mineruContent 是 MinerU 接入后的只读元数据：允许出现但不参与必填校验。
        // parseObject 签名为 (rawJson, requiredFields, allowedFields)：新增字段必须同步加入 allowedFields。
        JsonNode node = aiPipelineJsonValidator.parseObject(
                writeJsonSafely(structure),
                List.of("title", "documentType", "overview", "chapterOutline", "teachingFocus"),
                List.of(
                        "title", "documentType", "overview", "chapterOutline", "teachingFocus",
                        "rawMarkdown", "parseMode", "mineruContent"));
        aiPipelineJsonValidator.validateStringLength(node, "title", 120);
        aiPipelineJsonValidator.validateStringLength(node, "overview", 1200);
        aiPipelineJsonValidator.validateArraySize(node, "chapterOutline", 20);
        aiPipelineJsonValidator.validateArraySize(node, "teachingFocus", 20);

        String documentType = safeText(node.get("documentType"));
        if (!List.of("TEXTBOOK", "OUTLINE", "PAPER", "UNKNOWN").contains(documentType)) {
            throw new RuntimeException("Invalid documentType: " + documentType);
        }

        DocumentStructureDto normalized = objectMapper.convertValue(node, DocumentStructureDto.class);
        normalized.setTitle(trimToLength(normalized.getTitle(), 120));
        normalized.setOverview(trimToLength(normalized.getOverview(), 1200));
        normalized.setChapterOutline(normalizeShortList(normalized.getChapterOutline(), 20, 200));
        normalized.setTeachingFocus(normalizeShortList(normalized.getTeachingFocus(), 20, 200));
        return normalized;
    }

    private List<KnowledgePointDto> validateKnowledgePoints(List<KnowledgePointDto> knowledgePoints) {
        JsonNode node = aiPipelineJsonValidator.parseObject(
                writeJsonSafely(java.util.Map.of("knowledgePoints", knowledgePoints == null ? new ArrayList<>() : knowledgePoints)),
                List.of("knowledgePoints"),
                List.of("knowledgePoints"));
        aiPipelineJsonValidator.validateArraySize(node, "knowledgePoints", maxKnowledgePoints);

        JsonNode pointsNode = node.get("knowledgePoints");
        if (pointsNode == null || !pointsNode.isArray()) {
            throw new RuntimeException("knowledgePoints must be an array");
        }

        List<KnowledgePointDto> result = new ArrayList<>();
        for (JsonNode pointNode : pointsNode) {
            aiPipelineJsonValidator.parseObject(
                    pointNode.toString(),
                    List.of("pointName", "definition", "chapter", "importance", "evidenceSnippet", "resourceCitations"),
                    List.of("pointName", "definition", "chapter", "importance", "evidenceSnippet", "resourceCitations"));

            String importance = safeText(pointNode.get("importance"));
            if (!List.of("HIGH", "MEDIUM", "LOW").contains(importance)) {
                throw new RuntimeException("Invalid importance: " + importance);
            }

            KnowledgePointDto dto = objectMapper.convertValue(pointNode, KnowledgePointDto.class);
            dto.setPointName(trimToLength(dto.getPointName(), 120));
            dto.setDefinition(trimToLength(dto.getDefinition(), 500));
            dto.setChapter(trimToLength(dto.getChapter(), 120));
            dto.setEvidenceSnippet(trimToLength(dto.getEvidenceSnippet(), 300));
            dto.setResourceCitations(sanitizeResourceCitations(pointNode.get("resourceCitations")));
            if (safe(dto.getPointName()).isBlank()) {
                throw new RuntimeException("Knowledge point name cannot be empty");
            }
            result.add(dto);
        }
        return result;
    }

    private List<IdeologyMatchDto> validateIdeologyMatches(
            List<IdeologyMatchDto> ideologyMatches,
            List<KnowledgePointDto> knowledgePoints) {
        JsonNode node = aiPipelineJsonValidator.parseObject(
                writeJsonSafely(java.util.Map.of("ideologyMatches", ideologyMatches == null ? new ArrayList<>() : ideologyMatches)),
                List.of("ideologyMatches"),
                List.of("ideologyMatches"));
        aiPipelineJsonValidator.validateArraySize(
                node,
                "ideologyMatches",
                Math.max(1, maxKnowledgePoints * maxIdeologyMatchesPerPoint));

        List<String> availablePointNames = (knowledgePoints == null ? List.<KnowledgePointDto>of() : knowledgePoints).stream()
                .map(KnowledgePointDto::getPointName)
                .map(this::safe)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();

        JsonNode matchesNode = node.get("ideologyMatches");
        if (matchesNode == null || !matchesNode.isArray()) {
            throw new RuntimeException("ideologyMatches must be an array");
        }

        List<IdeologyMatchDto> result = new ArrayList<>();
        for (JsonNode matchNode : matchesNode) {
            aiPipelineJsonValidator.parseObject(
                    matchNode.toString(),
                    List.of("knowledgePointName", "ideologyElement", "matchReason", "confidence", "citationExplanation", "resourceCitations"),
                    List.of("knowledgePointName", "ideologyElement", "matchReason", "confidence", "citationExplanation", "resourceCitations"));

            int confidence = matchNode.get("confidence").asInt(-1);
            if (confidence < 0 || confidence > 100) {
                throw new RuntimeException("confidence must be in [0, 100]");
            }

            IdeologyMatchDto dto = objectMapper.convertValue(matchNode, IdeologyMatchDto.class);
            dto.setKnowledgePointName(trimToLength(dto.getKnowledgePointName(), 120));
            dto.setIdeologyElement(trimToLength(dto.getIdeologyElement(), 120));
            dto.setMatchReason(trimToLength(dto.getMatchReason(), 500));
            dto.setCitationExplanation(trimToLength(dto.getCitationExplanation(), 600));
            dto.setResourceCitations(sanitizeResourceCitations(matchNode.get("resourceCitations")));
            if (!availablePointNames.contains(safe(dto.getKnowledgePointName()))) {
                throw new RuntimeException("ideologyMatches.knowledgePointName must reference an existing knowledge point");
            }
            result.add(dto);
        }
        return result;
    }

    private TeachingArtifactsDto validateTeachingArtifacts(TeachingArtifactsDto artifacts) {
        if (artifacts == null) {
            throw new RuntimeException("Teaching artifacts cannot be empty");
        }

        JsonNode node = aiPipelineJsonValidator.parseObject(
                writeJsonSafely(artifacts),
                List.of("lectureNotes", "cases", "questions"),
                List.of("lectureNotes", "cases", "questions"));
        aiPipelineJsonValidator.validateStringLength(node, "lectureNotes", 5000);
        aiPipelineJsonValidator.validateArraySize(node, "cases", 12);
        aiPipelineJsonValidator.validateArraySize(node, "questions", maxQuestions);

        JsonNode questionsNode = node.get("questions");
        if (questionsNode == null || !questionsNode.isArray()) {
            throw new RuntimeException("questions must be an array");
        }
        for (JsonNode questionNode : questionsNode) {
            aiPipelineJsonValidator.parseObject(
                    questionNode.toString(),
                    List.of("stem", "referenceAnswer", "scoringPoints"),
                    List.of("questionType", "difficulty", "knowledgePointId", "stem", "options", "referenceAnswer",
                            "scoringPoints"));
            if (questionNode.get("options") != null && !questionNode.get("options").isArray()) {
                throw new RuntimeException("options must be an array");
            }
            if (questionNode.get("scoringPoints") == null || !questionNode.get("scoringPoints").isArray()) {
                throw new RuntimeException("scoringPoints must be an array");
            }
        }

        TeachingArtifactsDto dto = objectMapper.convertValue(node, TeachingArtifactsDto.class);
        dto.setLectureNotes(trimToLength(dto.getLectureNotes(), 5000));
        dto.setCases(normalizeShortList(dto.getCases(), 12, 2000));
        dto.setQuestions(normalizeQuestions(dto.getQuestions()));
        return dto;
    }

    private List<String> normalizeShortList(List<String> values, int maxSize, int maxLength) {
        List<String> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (normalized.size() >= maxSize) {
                break;
            }
            normalized.add(trimToLength(value, maxLength));
        }
        return normalized;
    }

    private List<TeachingArtifactsDto.QuestionDto> normalizeQuestions(List<TeachingArtifactsDto.QuestionDto> questions) {
        List<TeachingArtifactsDto.QuestionDto> normalized = new ArrayList<>();
        if (questions == null) {
            return normalized;
        }

        for (TeachingArtifactsDto.QuestionDto question : questions) {
            if (question == null) {
                continue;
            }
            TeachingArtifactsDto.QuestionDto item = new TeachingArtifactsDto.QuestionDto();
            item.setQuestionType(normalizeQuestionType(question.getQuestionType()));
            item.setDifficulty(normalizeDifficulty(question.getDifficulty()));
            item.setKnowledgePointId(question.getKnowledgePointId());
            item.setStem(trimToLength(question.getStem(), 2000));
            item.setOptions(normalizeShortList(question.getOptions(), 8, 1000));
            item.setReferenceAnswer(trimToLength(question.getReferenceAnswer(), 3000));
            item.setScoringPoints(normalizeShortList(question.getScoringPoints(), 12, 2000));
            normalized.add(item);
        }
        return normalized;
    }

    private void syncPipelineProjections(ParseTask task, PipelineResultDto result) {
        Long taskId = task.getId();
        deleteProjectionRows(taskId);

        String version = safe(result.getSchemaVersion()).isBlank() ? schemaVersion : result.getSchemaVersion();
        if (result.getKnowledgePoints() != null) {
            for (KnowledgePointDto dto : result.getKnowledgePoints()) {
                ParseTaskKnowledgePoint point = new ParseTaskKnowledgePoint();
                point.setParseTaskId(taskId);
                point.setCourseId(task.getCourseId());
                point.setPointName(dto.getPointName());
                point.setDefinition(dto.getDefinition());
                point.setChapter(dto.getChapter());
                point.setEvidenceSnippet(dto.getEvidenceSnippet());
                point.setResourceCitationsJson(writeJsonSafely(dto.getResourceCitations()));
                point.setPipelineVersion(version);
                parseTaskKnowledgePointMapper.insert(point);
            }
        }

        if (result.getIdeologyMatches() != null) {
            for (IdeologyMatchDto dto : result.getIdeologyMatches()) {
                ParseTaskIdeologyMatch match = new ParseTaskIdeologyMatch();
                match.setParseTaskId(taskId);
                match.setKnowledgePointName(dto.getKnowledgePointName());
                match.setIdeologyElement(dto.getIdeologyElement());
                match.setMatchReason(dto.getMatchReason());
                match.setCitationExplanation(dto.getCitationExplanation());
                match.setResourceCitationsJson(writeJsonSafely(dto.getResourceCitations()));
                match.setPipelineVersion(version);
                parseTaskIdeologyMatchMapper.insert(match);
            }
        }
    }

    private void deleteProjectionRows(Long taskId) {
        LambdaQueryWrapper<ParseTaskKnowledgePoint> pointWrapper = new LambdaQueryWrapper<>();
        pointWrapper.eq(ParseTaskKnowledgePoint::getParseTaskId, taskId);
        parseTaskKnowledgePointMapper.delete(pointWrapper);

        LambdaQueryWrapper<ParseTaskIdeologyMatch> matchWrapper = new LambdaQueryWrapper<>();
        matchWrapper.eq(ParseTaskIdeologyMatch::getParseTaskId, taskId);
        parseTaskIdeologyMatchMapper.delete(matchWrapper);
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize correction payload", ex);
        }
    }

    private List<ResourceCitationDto> sanitizeResourceCitations(JsonNode citationsNode) {
        List<ResourceCitationDto> citations = new ArrayList<>();
        if (citationsNode == null || !citationsNode.isArray()) {
            return citations;
        }
        int count = 0;
        for (JsonNode citationNode : citationsNode) {
            if (citationNode == null || !citationNode.isObject()) {
                continue;
            }
            aiPipelineJsonValidator.parseObject(
                    citationNode.toString(),
                    List.of(),
                    List.of("resourceRefId", "resourceId", "title", "source", "sourceUrl", "quotedExcerpt", "citationReason"));
            ResourceCitationDto dto = objectMapper.convertValue(citationNode, ResourceCitationDto.class);
            dto.setResourceRefId(trimToLength(dto.getResourceRefId(), 20));
            dto.setTitle(trimToLength(dto.getTitle(), 200));
            dto.setSource(trimToLength(dto.getSource(), 120));
            dto.setSourceUrl(trimToLength(dto.getSourceUrl(), 500));
            dto.setQuotedExcerpt(trimToLength(dto.getQuotedExcerpt(), 300));
            dto.setCitationReason(trimToLength(dto.getCitationReason(), 300));
            if (dto.getResourceRefId().isBlank() && dto.getTitle().isBlank() && dto.getQuotedExcerpt().isBlank()) {
                continue;
            }
            citations.add(dto);
            count++;
            if (count >= 3) {
                break;
            }
        }
        return citations;
    }

    private String trimToLength(String value, int maxLength) {
        String trimmed = safe(value);
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeText(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return "";
        }
        return node.asText().trim();
    }

    private String normalizeQuestionType(String value) {
        String normalized = safe(value).toUpperCase(Locale.ROOT);
        if (List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "SHORT_ANSWER", "CASE_ANALYSIS").contains(normalized)) {
            return normalized;
        }
        return "SHORT_ANSWER";
    }

    private String normalizeDifficulty(String value) {
        String normalized = safe(value).toUpperCase(Locale.ROOT);
        if (List.of("EASY", "MEDIUM", "HARD").contains(normalized)) {
            return normalized;
        }
        return "MEDIUM";
    }
}
