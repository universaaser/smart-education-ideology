package com.smartedu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartedu.common.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.CourseTeachingMaterialGroupDto;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.MaterialVersionItemDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.TeachingArtifactsDto;
import com.smartedu.dto.TeachingMaterialDraftDto;
import com.smartedu.dto.TeachingMaterialSaveRequestDto;
import com.smartedu.dto.TeachingMaterialViewDto;
import com.smartedu.dto.TeachingMaterialTraceDto;
import com.smartedu.dto.TeachingTraceItemDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.TeachingMaterial;
import com.smartedu.entity.TeachingMaterialTrace;
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

    private final TeachingMaterialMapper teachingMaterialMapper;
    private final ParseTaskMapper parseTaskMapper;
    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final AiIntelligenceService aiIntelligenceService;
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

        PipelineResultDto pipeline = aiIntelligenceService.getPipelineResult(taskId);
        return buildDraftFromPipeline(task, pipeline);
    }

    /**
     * Save draft without version bump.
     */
    @Transactional
    public TeachingMaterialDraftDto saveDraft(Long taskId, TeachingMaterialSaveRequestDto request) {
        ParseTask task = requireParseTask(taskId);
        PipelineResultDto pipeline = aiIntelligenceService.getPipelineResult(taskId);
        List<TeachingTraceItemDto> traceItems = buildTraceItems(taskId, pipeline);
        TeachingMaterial latest = findLatestByTaskId(taskId);

        if (latest == null) {
            TeachingMaterial created = new TeachingMaterial();
            created.setParseTaskId(taskId);
            created.setUserId(task.getUserId());
            created.setCourseId(task.getCourseId());
            applyEditableContent(created, request, task.getFileName());
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
            applyEditableContent(draft, request, task.getFileName());
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
        applyEditableContent(latest, request, task.getFileName());
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
        PipelineResultDto pipeline = aiIntelligenceService.getPipelineResult(taskId);
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
        applyEditableContent(version, request, task.getFileName());
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
            latest.setVersionNo(source.getVersionNo() == null ? 1 : source.getVersionNo());
            latest.setIsLatest(1);
            latest.setCreatedAt(LocalDateTime.now());
        }

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
            traceItems.add(traceItem);
        }
        return traceItems;
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
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(escapeMarkdownInline(firstNonBlank(material.getTitle(), "Teaching Material"))).append("\n\n");
        markdown.append("- Version: ").append(material.getVersionNo() == null ? 1 : material.getVersionNo()).append("\n");
        markdown.append("- Status: ").append(escapeMarkdownInline(firstNonBlank(material.getStatus(), STATUS_DRAFT))).append("\n");
        markdown.append("- Updated At: ").append(material.getUpdatedAt() == null ? "" : material.getUpdatedAt()).append("\n\n");

        markdown.append("## Lecture Notes\n\n");
        String notes = safe(material.getLectureNotes());
        markdown.append(notes.isBlank() ? "_No lecture notes._" : escapeMarkdownBlock(notes)).append("\n\n");

        markdown.append("## Cases\n\n");
        if (material.getCases() == null || material.getCases().isEmpty()) {
            markdown.append("_No cases._\n\n");
        } else {
            for (String caseItem : material.getCases()) {
                markdown.append("- ").append(escapeMarkdownInline(caseItem)).append("\n");
            }
            markdown.append("\n");
        }

        markdown.append("## Questions\n\n");
        if (material.getQuestions() == null || material.getQuestions().isEmpty()) {
            markdown.append("_No questions._\n\n");
        } else {
            int index = 1;
            for (TeachingArtifactsDto.QuestionDto question : material.getQuestions()) {
                markdown.append("### ").append(index).append(". ").append(escapeMarkdownInline(question.getStem())).append("\n\n");
                markdown.append("**Reference Answer**\n\n");
                markdown.append(escapeMarkdownBlock(question.getReferenceAnswer())).append("\n\n");
                markdown.append("**Scoring Points**\n\n");
                if (question.getScoringPoints() == null || question.getScoringPoints().isEmpty()) {
                    markdown.append("- _None._\n\n");
                } else {
                    for (String point : question.getScoringPoints()) {
                        markdown.append("- ").append(escapeMarkdownInline(point)).append("\n");
                    }
                    markdown.append("\n");
                }
                index++;
            }
        }

        markdown.append("## Trace Summary\n\n");
        if (material.getTraceItems() == null || material.getTraceItems().isEmpty()) {
            markdown.append("_No trace items._\n");
        } else {
            int index = 1;
            for (var trace : material.getTraceItems()) {
                markdown.append(index).append(". **Knowledge Point**: ")
                        .append(escapeMarkdownInline(trace.getKnowledgePointName())).append("\n");
                markdown.append("   - Ideology Element: ").append(escapeMarkdownInline(trace.getIdeologyElement())).append("\n");
                markdown.append("   - Evidence: ").append(escapeMarkdownInline(trace.getEvidenceSnippet())).append("\n");
                markdown.append("   - Reason: ").append(escapeMarkdownInline(trace.getMatchReason())).append("\n");
                index++;
            }
        }
        return markdown.toString();
    }

    private String escapeMarkdownInline(String text) {
        String normalized = safe(text);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("!", "\\!")
                .replace("|", "\\|");
    }

    private String escapeMarkdownBlock(String text) {
        String normalized = safe(text);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.lines()
                .map(this::escapeMarkdownInline)
                .collect(Collectors.joining("\n"));
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
            TeachingMaterialTrace row = new TeachingMaterialTrace();
            row.setMaterialId(materialId);
            row.setParseTaskId(taskId);
            row.setCourseId(courseId);
            row.setKnowledgePointId(item.getKnowledgePointId());
            row.setKnowledgePointName(trimToLength(safe(item.getKnowledgePointName()), 300));
            row.setIdeologyElement(trimToLength(safe(item.getIdeologyElement()), 300));
            row.setEvidenceSnippet(trimToLength(safe(item.getEvidenceSnippet()), 1000));
            row.setMatchReason(trimToLength(safe(item.getMatchReason()), 1000));
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
            TeachingArtifactsDto.QuestionDto sanitized = new TeachingArtifactsDto.QuestionDto();
            sanitized.setStem(trimToLength(safe(question.getStem()), 2000));
            sanitized.setReferenceAnswer(trimToLength(safe(question.getReferenceAnswer()), 3000));
            sanitized.setScoringPoints(safeList(question.getScoringPoints()));
            questions.add(sanitized);
        }
        return questions;
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
