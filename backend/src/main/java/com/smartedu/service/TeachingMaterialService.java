package com.smartedu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartedu.common.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.CourseTeachingMaterialGroupDto;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.MaterialVersionItemDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.ResourceCitationDto;
import com.smartedu.dto.TeachingArtifactsDto;
import com.smartedu.dto.TeachingMaterialDraftDto;
import com.smartedu.dto.TeachingMaterialSaveRequestDto;
import com.smartedu.dto.TeachingMaterialViewDto;
import com.smartedu.dto.TeachingMaterialTraceDto;
import com.smartedu.dto.TeachingTraceItemDto;
import com.smartedu.entity.CourseMaterialRule;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.TeachingMaterial;
import com.smartedu.entity.TeachingMaterialTrace;
import com.smartedu.mapper.CourseMaterialRuleMapper;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.TeachingMaterialMapper;
import com.smartedu.mapper.TeachingMaterialTraceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Teaching material editing service.
 *
 * <p>
 * This service bridges pipeline output and editable teacher content.
 * It supports draft save, publish save, and traceable read model.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeachingMaterialService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String RULE_MIN_LECTURE_CHARACTERS = "minLectureCharacters";
    private static final String RULE_REQUIRED_SECTIONS = "requiredSections";
    private static final String RULE_REQUIRE_IDEOLOGY_TAG_IN_CASES = "requireIdeologyTagInCases";
    private static final int DEFAULT_MIN_LECTURE_CHARACTERS = 0;

    private final TeachingMaterialMapper teachingMaterialMapper;
    private final CourseMaterialRuleMapper courseMaterialRuleMapper;
    private final ParseTaskMapper parseTaskMapper;
    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final AiIntelligenceService aiIntelligenceService;
    private final ParseTaskCorrectionService parseTaskCorrectionService;
    private final TeachingMaterialTraceMapper teachingMaterialTraceMapper;
    private final ObjectMapper objectMapper;

    private static final Comparator<TeachingMaterial> COURSE_GROUP_VERSION_COMPARATOR =
            Comparator.comparingInt((TeachingMaterial item) -> normalizeNumber(item.getIsLatest())).reversed()
                    .thenComparing(Comparator.comparingInt(
                            (TeachingMaterial item) -> normalizeNumber(item.getVersionNo())).reversed())
                    .thenComparing(TeachingMaterial::getUpdatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(TeachingMaterial::getId,
                            Comparator.nullsLast(Comparator.reverseOrder()));

    /**
     * Build editable draft for UI.
     *
     * <p>
     * If latest material exists, return it directly.
     * Otherwise build in-memory draft from pipeline result.
     */
    public TeachingMaterialDraftDto getEditorDraft(Long taskId) {
        ParseTask task = requireParseTask(taskId);
        TeachingMaterial latest = findLatestByTaskId(taskId);
        if (latest != null) {
            return toDraftDto(latest);
        }

        PipelineResultDto pipeline = resolveEffectivePipeline(task);
        return buildDraftFromPipeline(task, pipeline);
    }

    /**
     * Save draft without version bump.
     */
    @Transactional
    public TeachingMaterialDraftDto saveDraft(Long taskId, TeachingMaterialSaveRequestDto request) {
        ParseTask task = requireParseTask(taskId);
        requireMaterialUserId(task);
        TeachingMaterialSaveRequestDto normalizedRequest = normalizeSaveRequest(request);
        PipelineResultDto pipeline = resolveEffectivePipeline(task);
        List<TeachingTraceItemDto> traceItems = buildTraceItems(taskId, pipeline);
        TeachingMaterial latest = findLatestByTaskId(taskId);

        if (latest == null) {
            TeachingMaterial created = new TeachingMaterial();
            created.setParseTaskId(taskId);
            created.setUserId(task.getUserId());
            created.setCourseId(task.getCourseId());
            created.setChapterId(normalizedRequest.getChapterId());
            applyEditableContent(created, normalizedRequest, task.getFileName());
            created.setDocumentStructureJson(writeJsonSafely(pipeline == null ? null : pipeline.getDocumentStructure()));
            created.setKnowledgePointsJson(writeJsonSafely(pipeline == null ? null : pipeline.getKnowledgePoints()));
            created.setIdeologyMatchesJson(writeJsonSafely(pipeline == null ? null : pipeline.getIdeologyMatches()));
            created.setTraceJson(writeJsonSafely(traceItems));
            created.setSchemaVersion(resolveSchemaVersion(pipeline));
            created.setVersionNo(1);
            created.setIsLatest(1);
            created.setStatus(STATUS_DRAFT);
            created.setCreatedAt(LocalDateTime.now());
            created.setUpdatedAt(LocalDateTime.now());
            teachingMaterialMapper.insert(created);
            syncTraceRows(created.getId(), taskId, task.getCourseId(), traceItems, true);
            return toDraftDto(created);
        }

        if (STATUS_PUBLISHED.equalsIgnoreCase(safe(latest.getStatus()))) {
            latest.setIsLatest(0);
            latest.setUpdatedAt(LocalDateTime.now());
            teachingMaterialMapper.updateById(latest);

            TeachingMaterial draft = new TeachingMaterial();
            draft.setParseTaskId(taskId);
            draft.setUserId(task.getUserId());
            draft.setCourseId(task.getCourseId());
            draft.setChapterId(normalizedRequest.getChapterId());
            applyEditableContent(draft, normalizedRequest, task.getFileName());
            draft.setDocumentStructureJson(writeJsonSafely(pipeline == null ? null : pipeline.getDocumentStructure()));
            draft.setKnowledgePointsJson(writeJsonSafely(pipeline == null ? null : pipeline.getKnowledgePoints()));
            draft.setIdeologyMatchesJson(writeJsonSafely(pipeline == null ? null : pipeline.getIdeologyMatches()));
            draft.setTraceJson(writeJsonSafely(traceItems));
            draft.setSchemaVersion(resolveSchemaVersion(pipeline));
            draft.setVersionNo(latest.getVersionNo() == null ? 1 : latest.getVersionNo());
            draft.setIsLatest(1);
            draft.setStatus(STATUS_DRAFT);
            draft.setCreatedAt(LocalDateTime.now());
            draft.setUpdatedAt(LocalDateTime.now());
            teachingMaterialMapper.insert(draft);
            syncTraceRows(draft.getId(), taskId, task.getCourseId(), traceItems, true);
            return toDraftDto(draft);
        }

        latest.setCourseId(task.getCourseId());
        latest.setChapterId(normalizedRequest.getChapterId());
        applyEditableContent(latest, normalizedRequest, task.getFileName());
        latest.setDocumentStructureJson(writeJsonSafely(pipeline == null ? null : pipeline.getDocumentStructure()));
        latest.setKnowledgePointsJson(writeJsonSafely(pipeline == null ? null : pipeline.getKnowledgePoints()));
        latest.setIdeologyMatchesJson(writeJsonSafely(pipeline == null ? null : pipeline.getIdeologyMatches()));
        latest.setTraceJson(writeJsonSafely(traceItems));
        latest.setSchemaVersion(resolveSchemaVersion(pipeline));
        latest.setStatus(STATUS_DRAFT);
        latest.setUpdatedAt(LocalDateTime.now());
        teachingMaterialMapper.updateById(latest);
        syncTraceRows(latest.getId(), taskId, task.getCourseId(), traceItems, true);
        return toDraftDto(latest);
    }

    /**
     * Save a new published version.
     */
    @Transactional
    public TeachingMaterialViewDto savePublishedVersion(Long taskId, TeachingMaterialSaveRequestDto request) {
        ParseTask task = requireParseTask(taskId);
        requireMaterialUserId(task);
        TeachingMaterialSaveRequestDto normalizedRequest = normalizeSaveRequest(request);
        requireCompletePublishedQuestions(normalizedRequest.getQuestions());
        requireCourseMaterialRules(task.getCourseId(), normalizedRequest);
        PipelineResultDto pipeline = resolveEffectivePipeline(task);
        List<TeachingTraceItemDto> traceItems = buildTraceItems(taskId, pipeline);
        TeachingMaterial latest = findLatestByTaskId(taskId);

        if (latest != null) {
            latest.setIsLatest(0);
            latest.setUpdatedAt(LocalDateTime.now());
            teachingMaterialMapper.updateById(latest);
        }

        TeachingMaterial version = new TeachingMaterial();
        version.setParseTaskId(taskId);
        version.setUserId(task.getUserId());
        version.setCourseId(task.getCourseId());
        version.setChapterId(normalizedRequest.getChapterId());
        applyEditableContent(version, normalizedRequest, task.getFileName());
        version.setDocumentStructureJson(writeJsonSafely(pipeline == null ? null : pipeline.getDocumentStructure()));
        version.setKnowledgePointsJson(writeJsonSafely(pipeline == null ? null : pipeline.getKnowledgePoints()));
        version.setIdeologyMatchesJson(writeJsonSafely(pipeline == null ? null : pipeline.getIdeologyMatches()));
        version.setTraceJson(writeJsonSafely(traceItems));
        version.setSchemaVersion(resolveSchemaVersion(pipeline));
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        version.setIsLatest(1);
        version.setStatus(STATUS_PUBLISHED);
        version.setCreatedAt(LocalDateTime.now());
        version.setUpdatedAt(LocalDateTime.now());
        teachingMaterialMapper.insert(version);
        syncTraceRows(version.getId(), taskId, task.getCourseId(), traceItems, true);

        return toViewDto(version);
    }

    /**
     * Read one material version by id.
     */
    public TeachingMaterialViewDto getMaterialById(Long materialId) {
        TeachingMaterial material = teachingMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new RuntimeException("Teaching material not found");
        }
        return toViewDto(material);
    }

    /**
     * Query material versions by parse task id.
     */
    public List<MaterialVersionItemDto> getTaskMaterialVersions(Long taskId) {
        requireParseTask(taskId);
        LambdaQueryWrapper<TeachingMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeachingMaterial::getParseTaskId, taskId)
                .orderByDesc(TeachingMaterial::getVersionNo)
                .orderByDesc(TeachingMaterial::getUpdatedAt);
        return teachingMaterialMapper.selectList(wrapper).stream()
                .map(this::toVersionItem)
                .collect(Collectors.toList());
    }

    /**
     * Query saved teaching materials grouped by parse task under one course.
     */
    public List<CourseTeachingMaterialGroupDto> getCourseMaterialGroups(Long courseId) {
        LambdaQueryWrapper<TeachingMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeachingMaterial::getCourseId, courseId)
                .isNotNull(TeachingMaterial::getParseTaskId);
        List<TeachingMaterial> materials = teachingMaterialMapper.selectList(wrapper);
        if (materials.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<TeachingMaterial>> groupedByTask = new LinkedHashMap<>();
        for (TeachingMaterial material : materials) {
            if (material.getParseTaskId() == null) {
                continue;
            }
            groupedByTask.computeIfAbsent(material.getParseTaskId(), key -> new ArrayList<>()).add(material);
        }
        Map<Long, ParseTask> parseTaskMap = loadParseTaskMap(new ArrayList<>(groupedByTask.keySet()));

        List<CourseTeachingMaterialGroupDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<TeachingMaterial>> entry : groupedByTask.entrySet()) {
            List<TeachingMaterial> versions = new ArrayList<>(entry.getValue());
            versions.sort(COURSE_GROUP_VERSION_COMPARATOR);
            if (versions.isEmpty()) {
                continue;
            }

            TeachingMaterial latest = versions.get(0);
            ParseTask parseTask = parseTaskMap.get(entry.getKey());
            String sourceFileName = parseTask == null ? "" : safe(parseTask.getFileName());
            String displayTitle = firstNonBlank(safe(latest.getTitle()), sourceFileName);

            CourseTeachingMaterialGroupDto group = new CourseTeachingMaterialGroupDto();
            group.setParseTaskId(entry.getKey());
            group.setCourseId(courseId);
            group.setChapterId(latest.getChapterId());
            group.setDisplayTitle(displayTitle);
            group.setSourceFileName(sourceFileName);
            group.setLatestMaterialId(latest.getId());
            group.setLatestVersionNo(latest.getVersionNo());
            group.setLatestStatus(safe(latest.getStatus()));
            group.setUpdatedAt(latest.getUpdatedAt());
            group.setVersions(versions.stream()
                    .map(this::toVersionItem)
                    .collect(Collectors.toList()));
            result.add(group);
        }

        result.sort(Comparator.comparing(CourseTeachingMaterialGroupDto::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * Build markdown output for one saved material version.
     */
    public String exportMarkdownByMaterialId(Long materialId) {
        TeachingMaterialViewDto material = getMaterialById(materialId);
        return buildMarkdown(material);
    }

    /**
     * Build safe markdown export file name.
     */
    public String buildMarkdownFileName(Long materialId) {
        TeachingMaterialViewDto material = getMaterialById(materialId);
        String title = safe(material.getTitle());
        if (title.isBlank()) {
            title = "teaching-material";
        }
        String sanitized = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-_]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "");
        if (sanitized.isBlank()) {
            sanitized = "teaching-material";
        }
        return sanitized + "-v" + (material.getVersionNo() == null ? 1 : material.getVersionNo()) + ".md";
    }

    /**
     * Query trace rows under one parse task with filters.
     */
    public PageResult<TeachingMaterialTraceDto> getTaskTracePage(
            Long taskId,
            Long courseId,
            String knowledgePoint,
            String ideologyElement,
            int page,
            int size) {
        requireParseTask(taskId);
        syncLegacyTraceRowsByTask(taskId);

        Page<TeachingMaterialTrace> pageParam = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<TeachingMaterialTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeachingMaterialTrace::getParseTaskId, taskId);
        if (courseId != null) {
            wrapper.eq(TeachingMaterialTrace::getCourseId, courseId);
        }
        if (!safe(knowledgePoint).isBlank()) {
            wrapper.like(TeachingMaterialTrace::getKnowledgePointName, safe(knowledgePoint));
        }
        if (!safe(ideologyElement).isBlank()) {
            wrapper.like(TeachingMaterialTrace::getIdeologyElement, safe(ideologyElement));
        }
        wrapper.orderByDesc(TeachingMaterialTrace::getCreatedAt);

        Page<TeachingMaterialTrace> result = teachingMaterialTraceMapper.selectPage(pageParam, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(this::toTraceDto).collect(Collectors.toList()),
                result.getTotal(),
                result.getSize(),
                result.getCurrent());
    }

    /**
     * Query trace rows under one material version with filters.
     */
    public PageResult<TeachingMaterialTraceDto> getMaterialTracePage(
            Long materialId,
            String knowledgePoint,
            String ideologyElement,
            int page,
            int size) {
        TeachingMaterial material = teachingMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new RuntimeException("Teaching material not found");
        }
        ensureTraceRowsForMaterial(material);

        Page<TeachingMaterialTrace> pageParam = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<TeachingMaterialTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeachingMaterialTrace::getMaterialId, materialId);
        if (!safe(knowledgePoint).isBlank()) {
            wrapper.like(TeachingMaterialTrace::getKnowledgePointName, safe(knowledgePoint));
        }
        if (!safe(ideologyElement).isBlank()) {
            wrapper.like(TeachingMaterialTrace::getIdeologyElement, safe(ideologyElement));
        }
        wrapper.orderByDesc(TeachingMaterialTrace::getCreatedAt);

        Page<TeachingMaterialTrace> result = teachingMaterialTraceMapper.selectPage(pageParam, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(this::toTraceDto).collect(Collectors.toList()),
                result.getTotal(),
                result.getSize(),
                result.getCurrent());
    }

    /**
     * Rollback one historical version into current editable draft.
     */
    @Transactional
    public TeachingMaterialDraftDto rollbackToVersion(Long taskId, Long materialId) {
        ParseTask task = requireParseTask(taskId);
        TeachingMaterial source = teachingMaterialMapper.selectById(materialId);
        if (source == null) {
            throw new RuntimeException("Teaching material not found");
        }
        if (!taskId.equals(source.getParseTaskId())) {
            throw new RuntimeException("Material does not belong to this task");
        }

        TeachingMaterial latest = findLatestByTaskId(taskId);
        if (latest != null && STATUS_PUBLISHED.equalsIgnoreCase(safe(latest.getStatus()))) {
            latest.setIsLatest(0);
            latest.setUpdatedAt(LocalDateTime.now());
            teachingMaterialMapper.updateById(latest);
            latest = null;
        }

        if (latest == null) {
            latest = new TeachingMaterial();
            latest.setParseTaskId(taskId);
            latest.setUserId(task.getUserId());
            latest.setCourseId(source.getCourseId() == null ? task.getCourseId() : source.getCourseId());
            latest.setChapterId(source.getChapterId());
            latest.setVersionNo(source.getVersionNo() == null ? 1 : source.getVersionNo());
            latest.setIsLatest(1);
            latest.setCreatedAt(LocalDateTime.now());
        }

        latest.setChapterId(source.getChapterId());
        latest.setTitle(source.getTitle());
        latest.setLectureNotes(source.getLectureNotes());
        latest.setCasesJson(source.getCasesJson());
        latest.setQuestionsJson(source.getQuestionsJson());
        latest.setDocumentStructureJson(source.getDocumentStructureJson());
        latest.setKnowledgePointsJson(source.getKnowledgePointsJson());
        latest.setIdeologyMatchesJson(source.getIdeologyMatchesJson());
        latest.setTraceJson(source.getTraceJson());
        latest.setSchemaVersion(source.getSchemaVersion());
        latest.setStatus(STATUS_DRAFT);
        latest.setUpdatedAt(LocalDateTime.now());

        if (latest.getId() == null) {
            teachingMaterialMapper.insert(latest);
        } else {
            teachingMaterialMapper.updateById(latest);
        }

        syncTraceRows(latest.getId(), taskId, latest.getCourseId(), readTraceItems(source.getTraceJson()), true);
        return toDraftDto(latest);
    }

    private ParseTask requireParseTask(Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("Parse task not found");
        }
        return task;
    }

    private void requireMaterialUserId(ParseTask task) {
        if (task == null || task.getUserId() == null) {
            throw new IllegalArgumentException("Task user id is required before saving teaching material");
        }
    }

    private TeachingMaterialSaveRequestDto normalizeSaveRequest(TeachingMaterialSaveRequestDto request) {
        return request == null ? new TeachingMaterialSaveRequestDto() : request;
    }

    private PipelineResultDto resolveEffectivePipeline(ParseTask task) {
        PipelineResultDto fallback = aiIntelligenceService.getPipelineResult(task.getId());
        return parseTaskCorrectionService.resolveEffectivePipelineResult(task, fallback);
    }

    private TeachingMaterial findLatestByTaskId(Long taskId) {
        LambdaQueryWrapper<TeachingMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeachingMaterial::getParseTaskId, taskId)
                .eq(TeachingMaterial::getIsLatest, 1)
                .orderByDesc(TeachingMaterial::getVersionNo)
                .last("LIMIT 1");
        return teachingMaterialMapper.selectOne(wrapper);
    }

    private TeachingMaterialDraftDto buildDraftFromPipeline(ParseTask task, PipelineResultDto pipeline) {
        TeachingMaterialDraftDto draft = new TeachingMaterialDraftDto();
        draft.setMaterialId(null);
        draft.setParseTaskId(task.getId());
        draft.setUserId(task.getUserId());
        draft.setCourseId(task.getCourseId());
        draft.setChapterId(null);
        draft.setTitle(task.getFileName());
        draft.setLectureNotes(pipeline != null && pipeline.getTeachingArtifacts() != null
                ? safe(pipeline.getTeachingArtifacts().getLectureNotes())
                : "");
        draft.setCases(pipeline != null && pipeline.getTeachingArtifacts() != null
                ? safeList(pipeline.getTeachingArtifacts().getCases())
                : new ArrayList<>());
        draft.setQuestions(pipeline != null && pipeline.getTeachingArtifacts() != null
                ? safeQuestionList(pipeline.getTeachingArtifacts().getQuestions())
                : new ArrayList<>());
        draft.setTraceItems(buildTraceItems(task.getId(), pipeline));
        draft.setSchemaVersion(resolveSchemaVersion(pipeline));
        draft.setVersionNo(0);
        draft.setStatus(STATUS_DRAFT);
        draft.setUpdatedAt(LocalDateTime.now());
        return draft;
    }

    private void applyEditableContent(TeachingMaterial target, TeachingMaterialSaveRequestDto request, String defaultTitle) {
        target.setTitle(trimToLength(firstNonBlank(request.getTitle(), defaultTitle), 300));
        target.setLectureNotes(trimToLength(safe(request.getLectureNotes()), 10000));
        target.setCasesJson(writeJsonSafely(safeList(request.getCases())));
        target.setQuestionsJson(writeJsonSafely(safeQuestionList(request.getQuestions())));
    }

    /**
     * Build trace list from pipeline matches.
     *
     * <p>
     * If subject knowledge id cannot be found, keep null to preserve compatibility.
     */
    private List<TeachingTraceItemDto> buildTraceItems(Long taskId, PipelineResultDto pipeline) {
        List<TeachingTraceItemDto> traceItems = new ArrayList<>();
        if (pipeline == null || pipeline.getIdeologyMatches() == null) {
            return traceItems;
        }

        Map<String, Long> knowledgePointIdMap = loadSubjectKnowledgeIdMap(pipeline.getIdeologyMatches());
        Map<String, String> evidenceSnippetMap = loadEvidenceSnippetMap(pipeline.getKnowledgePoints());
        for (IdeologyMatchDto match : pipeline.getIdeologyMatches()) {
            if (match == null) {
                continue;
            }
            String knowledgePointName = safe(match.getKnowledgePointName());
            TeachingTraceItemDto traceItem = new TeachingTraceItemDto();
            traceItem.setParseTaskId(taskId);
            traceItem.setKnowledgePointName(knowledgePointName);
            traceItem.setKnowledgePointId(knowledgePointIdMap.get(knowledgePointName));
            traceItem.setIdeologyElement(safe(match.getIdeologyElement()));
            traceItem.setMatchReason(safe(match.getMatchReason()));
            traceItem.setEvidenceSnippet(firstNonBlank(evidenceSnippetMap.get(knowledgePointName), ""));
            traceItem.setCitationExplanation(safe(match.getCitationExplanation()));
            ResourceCitationDto citation = firstCitation(match.getResourceCitations());
            if (citation != null) {
                traceItem.setResourceTitle(safe(citation.getTitle()));
                traceItem.setResourceSource(safe(citation.getSource()));
                traceItem.setResourceSourceUrl(safe(citation.getSourceUrl()));
                traceItem.setResourceQuotedExcerpt(safe(citation.getQuotedExcerpt()));
            }
            traceItems.add(traceItem);
        }
        return traceItems;
    }

    private ResourceCitationDto firstCitation(List<ResourceCitationDto> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        for (ResourceCitationDto citation : citations) {
            if (citation != null) {
                return citation;
            }
        }
        return null;
    }

    private Map<Long, ParseTask> loadParseTaskMap(List<Long> taskIds) {
        Map<Long, ParseTask> parseTaskMap = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return parseTaskMap;
        }

        List<ParseTask> parseTasks = parseTaskMapper.selectBatchIds(taskIds);
        if (parseTasks == null || parseTasks.isEmpty()) {
            return parseTaskMap;
        }

        for (ParseTask parseTask : parseTasks) {
            if (parseTask == null || parseTask.getId() == null) {
                continue;
            }
            parseTaskMap.put(parseTask.getId(), parseTask);
        }
        return parseTaskMap;
    }

    /**
     * 这里按批量加载知识点 ID，避免每个 match 单独查一次数据库。
     */
    private Map<String, Long> loadSubjectKnowledgeIdMap(List<IdeologyMatchDto> matches) {
        Map<String, Long> idMap = new HashMap<>();
        if (matches == null || matches.isEmpty()) {
            return idMap;
        }

        List<String> knowledgePointNames = matches.stream()
                .filter(match -> match != null)
                .map(match -> safe(match.getKnowledgePointName()))
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
        if (knowledgePointNames.isEmpty()) {
            return idMap;
        }

        LambdaQueryWrapper<SubjectKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SubjectKnowledge::getName, knowledgePointNames)
                .orderByAsc(SubjectKnowledge::getId);
        List<SubjectKnowledge> subjectKnowledgeList = subjectKnowledgeMapper.selectList(wrapper);
        if (subjectKnowledgeList == null || subjectKnowledgeList.isEmpty()) {
            return idMap;
        }

        for (SubjectKnowledge subjectKnowledge : subjectKnowledgeList) {
            if (subjectKnowledge == null || subjectKnowledge.getId() == null) {
                continue;
            }
            String normalizedName = safe(subjectKnowledge.getName());
            if (!normalizedName.isBlank()) {
                idMap.putIfAbsent(normalizedName, subjectKnowledge.getId());
            }
        }
        return idMap;
    }

    private Map<String, String> loadEvidenceSnippetMap(List<KnowledgePointDto> points) {
        Map<String, String> evidenceMap = new HashMap<>();
        if (points == null || points.isEmpty()) {
            return evidenceMap;
        }

        for (KnowledgePointDto point : points) {
            if (point == null) {
                continue;
            }
            String normalizedName = safe(point.getPointName());
            if (!normalizedName.isBlank()) {
                evidenceMap.putIfAbsent(normalizedName, trimToLength(safe(point.getEvidenceSnippet()), 500));
            }
        }
        return evidenceMap;
    }

    private TeachingMaterialDraftDto toDraftDto(TeachingMaterial material) {
        TeachingMaterialDraftDto draft = new TeachingMaterialDraftDto();
        draft.setMaterialId(material.getId());
        draft.setParseTaskId(material.getParseTaskId());
        draft.setUserId(material.getUserId());
        draft.setCourseId(material.getCourseId());
        draft.setChapterId(material.getChapterId());
        draft.setTitle(safe(material.getTitle()));
        draft.setLectureNotes(safe(material.getLectureNotes()));
        draft.setCases(readCases(material.getCasesJson()));
        draft.setQuestions(readQuestions(material.getQuestionsJson()));
        draft.setTraceItems(readTraceItems(material.getTraceJson()));
        draft.setSchemaVersion(safe(material.getSchemaVersion()));
        draft.setVersionNo(material.getVersionNo());
        draft.setStatus(safe(material.getStatus()));
        draft.setUpdatedAt(material.getUpdatedAt());
        return draft;
    }

    private TeachingMaterialViewDto toViewDto(TeachingMaterial material) {
        TeachingMaterialViewDto view = new TeachingMaterialViewDto();
        view.setMaterialId(material.getId());
        view.setParseTaskId(material.getParseTaskId());
        view.setUserId(material.getUserId());
        view.setCourseId(material.getCourseId());
        view.setChapterId(material.getChapterId());
        view.setTitle(safe(material.getTitle()));
        view.setLectureNotes(safe(material.getLectureNotes()));
        view.setCases(readCases(material.getCasesJson()));
        view.setQuestions(readQuestions(material.getQuestionsJson()));
        view.setTraceItems(readTraceItems(material.getTraceJson()));
        view.setSchemaVersion(safe(material.getSchemaVersion()));
        view.setVersionNo(material.getVersionNo());
        view.setStatus(safe(material.getStatus()));
        view.setIsLatest(material.getIsLatest());
        view.setCreatedAt(material.getCreatedAt());
        view.setUpdatedAt(material.getUpdatedAt());
        return view;
    }

    private MaterialVersionItemDto toVersionItem(TeachingMaterial material) {
        MaterialVersionItemDto item = new MaterialVersionItemDto();
        item.setMaterialId(material.getId());
        item.setVersionNo(material.getVersionNo());
        item.setStatus(safe(material.getStatus()));
        item.setIsLatest(material.getIsLatest());
        item.setUpdatedAt(material.getUpdatedAt());
        return item;
    }

    private String buildMarkdown(TeachingMaterialViewDto material) {
        // Note: lecture notes, cases, reference answers and Selection Explanation
        // content may already be authored as Markdown (AI answers usually contain
        // #/*/- markers, and teachers may directly type Markdown). The previous
        // implementation escaped every markdown character, which destroyed the
        // formatting of exported .md files. We now treat these fields as
        // "content is markdown" and only normalize characters that would really
        // break the surrounding structure (e.g. line breaks inside headings).
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(sanitizeHeading(firstNonBlank(material.getTitle(), "Teaching Material"))).append("\n\n");
        markdown.append("- Version: ").append(material.getVersionNo() == null ? 1 : material.getVersionNo()).append("\n");
        markdown.append("- Status: ").append(sanitizeInline(firstNonBlank(material.getStatus(), STATUS_DRAFT))).append("\n");
        markdown.append("- Updated At: ").append(material.getUpdatedAt() == null ? "" : material.getUpdatedAt()).append("\n\n");

        markdown.append("## Lecture Notes\n\n");
        String notes = safe(material.getLectureNotes());
        markdown.append(notes.isBlank() ? "_No lecture notes._" : notes).append("\n\n");

        markdown.append("## Teaching Cases\n\n");
        if (material.getCases() == null || material.getCases().isEmpty()) {
            markdown.append("_No cases._\n\n");
        } else {
            int index = 1;
            for (String caseItem : material.getCases()) {
                markdown.append("### Case ").append(index).append("\n\n");
                String caseContent = safe(caseItem);
                markdown.append(caseContent.isBlank() ? "_No case content._" : caseContent).append("\n\n");
                index++;
            }
        }

        markdown.append("## Assessment Questions\n\n");
        if (material.getQuestions() == null || material.getQuestions().isEmpty()) {
            markdown.append("_No questions._\n\n");
        } else {
            int index = 1;
            for (TeachingArtifactsDto.QuestionDto question : material.getQuestions()) {
                markdown.append("### Question ").append(index).append("\n\n");
                markdown.append("**Type**: ").append(sanitizeInline(firstNonBlank(question.getQuestionType(), "SHORT_ANSWER"))).append("\n\n");
                markdown.append("**Difficulty**: ").append(sanitizeInline(firstNonBlank(question.getDifficulty(), "MEDIUM"))).append("\n\n");
                if (question.getKnowledgePointId() != null) {
                    markdown.append("**Knowledge Point ID**: ").append(question.getKnowledgePointId()).append("\n\n");
                }
                markdown.append("**Stem**\n\n");
                String stem = safe(question.getStem());
                markdown.append(stem.isBlank() ? "_No question stem._" : stem).append("\n\n");
                if (question.getOptions() != null && !question.getOptions().isEmpty()) {
                    markdown.append("**Options**\n\n");
                    for (String option : question.getOptions()) {
                        markdown.append(formatListItem(option)).append("\n");
                    }
                    markdown.append("\n");
                }
                markdown.append("**Reference Answer**\n\n");
                String referenceAnswer = safe(question.getReferenceAnswer());
                markdown.append(referenceAnswer.isBlank() ? "_No reference answer._" : referenceAnswer).append("\n\n");
                markdown.append("**Scoring Points**\n\n");
                if (question.getScoringPoints() == null || question.getScoringPoints().isEmpty()) {
                    markdown.append("- _None._\n\n");
                } else {
                    for (String point : question.getScoringPoints()) {
                        markdown.append(formatListItem(point)).append("\n");
                    }
                    markdown.append("\n");
                }
                index++;
            }
        }

        markdown.append("## Ideology Integration\n\n");
        if (material.getTraceItems() == null || material.getTraceItems().isEmpty()) {
            markdown.append("_No ideology integration records._\n");
        } else {
            int index = 1;
            for (var trace : material.getTraceItems()) {
                markdown.append("### Integration ").append(index).append("\n\n");
                markdown.append("- **Knowledge Point**: ")
                        .append(sanitizeInline(trace.getKnowledgePointName())).append("\n");
                markdown.append("- **Ideology Element**: ").append(sanitizeInline(trace.getIdeologyElement())).append("\n");
                markdown.append("- **Evidence**: ").append(sanitizeInline(trace.getEvidenceSnippet())).append("\n");
                markdown.append("- **Integration Reason**: ").append(sanitizeInline(trace.getMatchReason())).append("\n");
                if (!safe(trace.getResourceTitle()).isBlank()) {
                    markdown.append("- **Resource Title**: ").append(sanitizeInline(trace.getResourceTitle())).append("\n");
                }
                if (!safe(trace.getResourceSource()).isBlank()) {
                    markdown.append("- **Resource Source**: ").append(sanitizeInline(trace.getResourceSource())).append("\n");
                }
                if (!safe(trace.getResourceSourceUrl()).isBlank()) {
                    markdown.append("- **Resource URL**: ").append(sanitizeInline(trace.getResourceSourceUrl())).append("\n");
                }
                if (!safe(trace.getResourceQuotedExcerpt()).isBlank()) {
                    markdown.append("- **Resource Excerpt**: ").append(sanitizeInline(trace.getResourceQuotedExcerpt())).append("\n");
                }
                if (!safe(trace.getCitationExplanation()).isBlank()) {
                    markdown.append("- **Citation Explanation**: ").append(sanitizeInline(trace.getCitationExplanation())).append("\n");
                }
                markdown.append("\n");
                index++;
            }
        }
        return markdown.toString();
    }

    /**
     * Flatten a heading to a single line. Line breaks inside a heading would
     * break the markdown structure, while other characters are kept verbatim
     * so that embedded markdown is preserved as-is.
     */
    private String sanitizeHeading(String text) {
        String normalized = safe(text);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.replace('\n', ' ').replace('\r', ' ').trim();
    }

    /**
     * Minimal cleanup for inline values used on single-line rows (e.g.
     * "Knowledge Point: xxx" trace rows): only collapse line breaks so the
     * surrounding row keeps its structure; everything else is left intact so
     * markdown formatting (emphasis, inline code) still renders.
     */
    private String sanitizeInline(String text) {
        return sanitizeHeading(text);
    }

    /**
     * Build one markdown list item. When the value contains multiple lines,
     * subsequent lines are indented with two spaces so they stay attached to
     * the same list item without breaking the enclosing list structure.
     */
    private String formatListItem(String text) {
        String normalized = safe(text);
        if (normalized.isBlank()) {
            return "- ";
        }
        String[] lines = normalized.split("\\r?\\n");
        StringBuilder buffer = new StringBuilder();
        buffer.append("- ").append(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            buffer.append("\n  ").append(lines[i]);
        }
        return buffer.toString();
    }

    private TeachingMaterialTraceDto toTraceDto(TeachingMaterialTrace trace) {
        TeachingMaterialTraceDto dto = new TeachingMaterialTraceDto();
        dto.setId(trace.getId());
        dto.setMaterialId(trace.getMaterialId());
        dto.setParseTaskId(trace.getParseTaskId());
        dto.setCourseId(trace.getCourseId());
        dto.setKnowledgePointId(trace.getKnowledgePointId());
        dto.setKnowledgePointName(safe(trace.getKnowledgePointName()));
        dto.setIdeologyElement(safe(trace.getIdeologyElement()));
        dto.setEvidenceSnippet(safe(trace.getEvidenceSnippet()));
        dto.setMatchReason(safe(trace.getMatchReason()));
        dto.setResourceTitle(safe(trace.getResourceTitle()));
        dto.setResourceSource(safe(trace.getResourceSource()));
        dto.setResourceSourceUrl(safe(trace.getResourceSourceUrl()));
        dto.setResourceQuotedExcerpt(safe(trace.getResourceQuotedExcerpt()));
        dto.setCitationExplanation(safe(trace.getCitationExplanation()));
        dto.setCreatedAt(trace.getCreatedAt());
        return dto;
    }

    private void syncLegacyTraceRowsByTask(Long taskId) {
        LambdaQueryWrapper<TeachingMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeachingMaterial::getParseTaskId, taskId);
        List<TeachingMaterial> materials = teachingMaterialMapper.selectList(wrapper);
        for (TeachingMaterial material : materials) {
            ensureTraceRowsForMaterial(material);
        }
    }

    private void ensureTraceRowsForMaterial(TeachingMaterial material) {
        if (material == null || material.getId() == null) {
            return;
        }
        LambdaQueryWrapper<TeachingMaterialTrace> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(TeachingMaterialTrace::getMaterialId, material.getId());
        Long count = teachingMaterialTraceMapper.selectCount(countWrapper);
        if (count != null && count > 0) {
            return;
        }
        syncTraceRows(
                material.getId(),
                material.getParseTaskId(),
                material.getCourseId(),
                readTraceItems(material.getTraceJson()),
                true);
    }

    private void syncTraceRows(
            Long materialId,
            Long taskId,
            Long courseId,
            List<TeachingTraceItemDto> traceItems,
            boolean overwrite) {
        if (materialId == null || taskId == null) {
            return;
        }
        if (overwrite) {
            LambdaQueryWrapper<TeachingMaterialTrace> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(TeachingMaterialTrace::getMaterialId, materialId);
            teachingMaterialTraceMapper.delete(deleteWrapper);
        }

        if (traceItems == null || traceItems.isEmpty()) {
            return;
        }

        for (TeachingTraceItemDto item : traceItems) {
            if (item == null) {
                continue;
            }
            String knowledgePointName = trimToLength(safe(item.getKnowledgePointName()), 300);
            String ideologyElement = trimToLength(safe(item.getIdeologyElement()), 300);
            if (knowledgePointName.isBlank() || ideologyElement.isBlank()) {
                continue;
            }
            TeachingMaterialTrace row = new TeachingMaterialTrace();
            row.setMaterialId(materialId);
            row.setParseTaskId(taskId);
            row.setCourseId(courseId);
            row.setKnowledgePointId(item.getKnowledgePointId());
            row.setKnowledgePointName(knowledgePointName);
            row.setIdeologyElement(ideologyElement);
            row.setEvidenceSnippet(trimToLength(safe(item.getEvidenceSnippet()), 1000));
            row.setMatchReason(trimToLength(safe(item.getMatchReason()), 1000));
            row.setResourceTitle(trimToLength(safe(item.getResourceTitle()), 300));
            row.setResourceSource(trimToLength(safe(item.getResourceSource()), 200));
            row.setResourceSourceUrl(trimToLength(safe(item.getResourceSourceUrl()), 500));
            row.setResourceQuotedExcerpt(trimToLength(safe(item.getResourceQuotedExcerpt()), 1000));
            row.setCitationExplanation(trimToLength(safe(item.getCitationExplanation()), 1000));
            row.setCreatedAt(LocalDateTime.now());
            teachingMaterialTraceMapper.insert(row);
        }
    }

    private String resolveSchemaVersion(PipelineResultDto pipeline) {
        if (pipeline == null || safe(pipeline.getSchemaVersion()).isBlank()) {
            return "v1";
        }
        return pipeline.getSchemaVersion();
    }

    private List<String> readCases(String casesJson) {
        if (casesJson == null || casesJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(casesJson, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.warn("Failed to deserialize cases json: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private List<TeachingArtifactsDto.QuestionDto> readQuestions(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(questionsJson, new TypeReference<List<TeachingArtifactsDto.QuestionDto>>() {});
        } catch (Exception ex) {
            log.warn("Failed to deserialize questions json: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private List<TeachingTraceItemDto> readTraceItems(String traceJson) {
        if (traceJson == null || traceJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(traceJson, new TypeReference<List<TeachingTraceItemDto>>() {});
        } catch (Exception ex) {
            log.warn("Failed to deserialize trace json: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new ArrayList<>() : value);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize teaching material json", ex);
        }
    }

    private List<String> safeList(List<String> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        return source.stream()
                .map(this::safe)
                .filter(item -> !item.isBlank())
                .map(item -> trimToLength(item, 2000))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<TeachingArtifactsDto.QuestionDto> safeQuestionList(List<TeachingArtifactsDto.QuestionDto> source) {
        List<TeachingArtifactsDto.QuestionDto> questions = new ArrayList<>();
        if (source == null) {
            return questions;
        }

        for (TeachingArtifactsDto.QuestionDto question : source) {
            if (question == null) {
                continue;
            }
            String stem = trimToLength(safe(question.getStem()), 2000);
            String referenceAnswer = trimToLength(safe(question.getReferenceAnswer()), 3000);
            if (stem.isBlank() || referenceAnswer.isBlank()) {
                continue;
            }
            TeachingArtifactsDto.QuestionDto sanitized = new TeachingArtifactsDto.QuestionDto();
            sanitized.setQuestionType(normalizeQuestionType(question.getQuestionType()));
            sanitized.setDifficulty(normalizeDifficulty(question.getDifficulty()));
            sanitized.setKnowledgePointId(question.getKnowledgePointId());
            sanitized.setStem(stem);
            sanitized.setOptions(safeList(question.getOptions()));
            sanitized.setReferenceAnswer(referenceAnswer);
            sanitized.setScoringPoints(safeList(question.getScoringPoints()));
            questions.add(sanitized);
        }
        return questions;
    }

    private void requireCompletePublishedQuestions(List<TeachingArtifactsDto.QuestionDto> source) {
        if (source == null) {
            return;
        }
        for (TeachingArtifactsDto.QuestionDto question : source) {
            if (question == null) {
                continue;
            }
            String referenceAnswer = safe(question.getReferenceAnswer());
            if (safe(question.getStem()).isBlank() || referenceAnswer.isBlank()) {
                throw new IllegalArgumentException("Published assessment questions require stem and reference answer");
            }
            String questionType = normalizeQuestionType(question.getQuestionType());
            List<String> options = safeList(question.getOptions());
            if (("SINGLE_CHOICE".equals(questionType) || "MULTIPLE_CHOICE".equals(questionType)) && options.isEmpty()) {
                throw new IllegalArgumentException("Choice assessment questions require options");
            }
            if ("SINGLE_CHOICE".equals(questionType) && !isChoiceAnswerCovered(referenceAnswer, options)) {
                throw new IllegalArgumentException("Single choice reference answer must match one option");
            }
            if ("MULTIPLE_CHOICE".equals(questionType) && !areChoiceAnswersCovered(referenceAnswer, options)) {
                throw new IllegalArgumentException("Multiple choice reference answers must match options");
            }
        }
    }

    private void requireCourseMaterialRules(Long courseId, TeachingMaterialSaveRequestDto request) {
        if (courseId == null) {
            return;
        }
        CourseMaterialRule rule = loadCourseMaterialRule(courseId);
        if (rule == null || safe(rule.getRuleJson()).isBlank()) {
            return;
        }
        CourseMaterialRuleConfig config = readCourseMaterialRuleConfig(rule.getRuleJson());
        int lectureCharacters = countNonWhitespaceCharacters(request == null ? null : request.getLectureNotes());
        if (lectureCharacters < config.minLectureCharacters()) {
            throw new IllegalArgumentException("Lecture notes do not satisfy course minimum length rule");
        }
        for (String section : config.requiredSections()) {
            if (!containsText(request == null ? null : request.getLectureNotes(), section)) {
                throw new IllegalArgumentException("Lecture notes are missing required course section: " + section);
            }
        }
        if (config.requireIdeologyTagInCases()) {
            List<String> cases = request == null ? null : request.getCases();
            if (cases == null || cases.stream().noneMatch(this::containsIdeologyTag)) {
                throw new IllegalArgumentException("Teaching cases require at least one ideology tag");
            }
        }
    }

    private CourseMaterialRule loadCourseMaterialRule(Long courseId) {
        LambdaQueryWrapper<CourseMaterialRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseMaterialRule::getCourseId, courseId)
                .orderByDesc(CourseMaterialRule::getUpdatedAt)
                .orderByDesc(CourseMaterialRule::getId)
                .last("LIMIT 1");
        return courseMaterialRuleMapper.selectOne(wrapper);
    }

    private CourseMaterialRuleConfig readCourseMaterialRuleConfig(String ruleJson) {
        try {
            JsonNode node = objectMapper.readTree(ruleJson);
            int minLectureCharacters = Math.max(
                    node.path(RULE_MIN_LECTURE_CHARACTERS).asInt(DEFAULT_MIN_LECTURE_CHARACTERS), 0);
            List<String> requiredSections = new ArrayList<>();
            JsonNode sections = node.path(RULE_REQUIRED_SECTIONS);
            if (sections.isArray()) {
                sections.forEach(section -> {
                    String value = trimToLength(safe(section.asText()), 120);
                    if (!value.isBlank()) {
                        requiredSections.add(value);
                    }
                });
            }
            boolean requireIdeologyTagInCases = node.path(RULE_REQUIRE_IDEOLOGY_TAG_IN_CASES).asBoolean(false);
            return new CourseMaterialRuleConfig(minLectureCharacters, requiredSections, requireIdeologyTagInCases);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Course material rule json is invalid");
        }
    }

    private int countNonWhitespaceCharacters(String text) {
        String normalized = safe(text);
        if (normalized.isBlank()) {
            return 0;
        }
        return normalized.replaceAll("\\s+", "").length();
    }

    private boolean containsText(String content, String expected) {
        return safe(content).toLowerCase(Locale.ROOT).contains(safe(expected).toLowerCase(Locale.ROOT));
    }

    private boolean containsIdeologyTag(String caseItem) {
        String normalized = safe(caseItem).toLowerCase(Locale.ROOT);
        return normalized.contains("[ideology:") || normalized.contains("ideology tag:");
    }

    private record CourseMaterialRuleConfig(
            int minLectureCharacters,
            List<String> requiredSections,
            boolean requireIdeologyTagInCases) {
    }

    private boolean areChoiceAnswersCovered(String referenceAnswer, List<String> options) {
        List<String> answers = splitChoiceAnswers(referenceAnswer);
        if (answers.isEmpty()) {
            return false;
        }
        return answers.stream().allMatch(answer -> isChoiceAnswerCovered(answer, options));
    }

    private boolean isChoiceAnswerCovered(String answer, List<String> options) {
        String normalizedAnswer = normalizeChoiceText(answer);
        if (normalizedAnswer.isBlank()) {
            return false;
        }
        for (int index = 0; index < options.size(); index++) {
            String option = safe(options.get(index));
            if (normalizeChoiceText(option).equals(normalizedAnswer)) {
                return true;
            }
            if (normalizeChoiceText(stripOptionLabel(option)).equals(normalizedAnswer)) {
                return true;
            }
            if (normalizeChoiceText(optionLabel(index)).equals(normalizedAnswer)) {
                return true;
            }
            if (String.valueOf(index + 1).equals(normalizedAnswer)) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitChoiceAnswers(String referenceAnswer) {
        return List.of(safe(referenceAnswer).split("[,，;；、/]+"))
                .stream()
                .map(this::safe)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toList());
    }

    private String stripOptionLabel(String option) {
        return safe(option).replaceFirst("^[A-Za-z0-9]+[\\.、:)）\\s-]+", "").trim();
    }

    private String optionLabel(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private String normalizeChoiceText(String value) {
        return safe(value).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
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

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return safe(second);
    }

    private String trimToLength(String value, int maxLength) {
        String normalized = safe(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizeNumber(Integer value) {
        return value == null ? 0 : value;
    }
}
