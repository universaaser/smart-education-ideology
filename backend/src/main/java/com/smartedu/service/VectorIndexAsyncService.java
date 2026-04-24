package com.smartedu.service;

import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.dto.ResourceCitationDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.SelectionExplainRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3A: async vector indexing bridge to avoid @Async self-invocation issue.
 *
 * <p>
 * Called by AiIntelligenceService after pipeline completion. Runs in a separate
 * thread so Qdrant/embedding latency does not block the task status update.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorIndexAsyncService {

    private final VectorIndexService vectorIndexService;

    @Async
    public void indexPipelineResult(ParseTask task, PipelineResultDto result) {
        if (task == null || task.getId() == null || result == null) {
            return;
        }
        Long taskId = task.getId();
        String version = result.getSchemaVersion();
        if (version == null || version.isBlank()) {
            version = "v1";
        }

        try {
            deletePipelineResult(taskId);

            List<KnowledgePointDto> kps = result.getKnowledgePoints();
            if (kps != null && !kps.isEmpty()) {
                List<String> texts = kps.stream()
                        .map(dto -> safeJoin(dto.getPointName(), dto.getDefinition(), dto.getEvidenceSnippet()))
                        .toList();
                List<float[]> vectors = vectorIndexService.embedBatch(texts);
                if (vectors != null) {
                    String kpCollection = vectorIndexService.resolveCollection("knowledge_points");
                    for (int i = 0; i < kps.size(); i++) {
                        float[] vector = i < vectors.size() ? vectors.get(i) : null;
                        if (vector == null) continue;
                        KnowledgePointDto dto = kps.get(i);
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("title", dto.getPointName());
                        payload.put("snippet", dto.getDefinition());
                        payload.put("source_type", "parse_task_knowledge_point");
                        payload.put("source_id", taskId);
                        putCitationPayload(payload, firstCitation(dto.getResourceCitations()));
                        payload.put("chapter", dto.getChapter());
                        payload.put("pipeline_version", version);
                        vectorIndexService.upsert(kpCollection, taskId + "_kp_" + i, vector, payload);
                    }
                }
            }

            List<IdeologyMatchDto> ims = result.getIdeologyMatches();
            if (ims != null && !ims.isEmpty()) {
                List<String> texts = ims.stream()
                        .map(dto -> safeJoin(dto.getIdeologyElement(), dto.getMatchReason(), dto.getKnowledgePointName()))
                        .toList();
                List<float[]> vectors = vectorIndexService.embedBatch(texts);
                if (vectors != null) {
                    String imCollection = vectorIndexService.resolveCollection("ideology_matches");
                    for (int i = 0; i < ims.size(); i++) {
                        float[] vector = i < vectors.size() ? vectors.get(i) : null;
                        if (vector == null) continue;
                        IdeologyMatchDto dto = ims.get(i);
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("title", dto.getIdeologyElement());
                        payload.put("snippet", dto.getMatchReason());
                        payload.put("source_type", "parse_task_ideology_match");
                        payload.put("source_id", taskId);
                        putCitationPayload(payload, firstCitation(dto.getResourceCitations()));
                        payload.put("knowledge_point_name", dto.getKnowledgePointName());
                        payload.put("pipeline_version", version);
                        vectorIndexService.upsert(imCollection, taskId + "_im_" + i, vector, payload);
                    }
                }
            }

            log.info("Async vector indexing completed for taskId={}", taskId);
        } catch (Exception ex) {
            log.error("Async vector indexing failed for taskId={}", taskId, ex);
        }
    }

    /**
     * Remove previously indexed pipeline vectors for one parse task.
     */
    public void deletePipelineResult(Long taskId) {
        if (taskId == null) {
            return;
        }
        try {
            vectorIndexService.deleteBySource(
                    vectorIndexService.resolveCollection("knowledge_points"),
                    "parse_task_knowledge_point",
                    taskId);
            vectorIndexService.deleteBySource(
                    vectorIndexService.resolveCollection("ideology_matches"),
                    "parse_task_ideology_match",
                    taskId);
        } catch (Exception ex) {
            log.error("Delete pipeline vectors failed for taskId={}", taskId, ex);
        }
    }

    @Async
    public void indexSelectionExplainRecord(SelectionExplainRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }
        String text = safeJoin(record.getSelectedText(), record.getAnswer());
        if (text.isBlank()) {
            return;
        }
        try {
            float[] vector = vectorIndexService.embed(text);
            if (vector == null) {
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", truncate(record.getSelectedText(), 80));
            payload.put("snippet", record.getAnswer());
            payload.put("source_type", "selection_explain_record");
            payload.put("source_id", record.getId());
            payload.put("course_id", record.getCourseId());
            payload.put("material_id", record.getMaterialId());
            payload.put("parse_task_id", record.getParseTaskId());
            payload.put("has_reliable_evidence", Integer.valueOf(1).equals(record.getHasReliableEvidence()));

            vectorIndexService.upsert(
                    vectorIndexService.resolveCollection("selection_explain"),
                    "selection_explain_" + record.getId(),
                    vector,
                    payload);
        } catch (Exception ex) {
            log.error("Async selection explain indexing failed for recordId={}", record.getId(), ex);
        }
    }

    private ResourceCitationDto firstCitation(List<ResourceCitationDto> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        return citations.stream()
                .filter(citation -> !safeJoin(citation.getSource(), citation.getSourceUrl()).isBlank())
                .findFirst()
                .orElse(citations.get(0));
    }

    private void putCitationPayload(Map<String, Object> payload, ResourceCitationDto citation) {
        if (citation == null) {
            return;
        }
        payload.put("source", citation.getSource());
        payload.put("source_url", citation.getSourceUrl());
    }

    private String safeJoin(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    private String truncate(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
