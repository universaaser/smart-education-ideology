package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.IdeologyMatchDto;
import com.smartedu.dto.KnowledgePointDto;
import com.smartedu.dto.PipelineResultDto;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.ParseTaskIdeologyMatch;
import com.smartedu.entity.ParseTaskKnowledgePoint;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.mapper.ParseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 2: backfill projection tables from existing parse_tasks.ai_analysis JSON.
 *
 * <p>
 * Runs once at startup. Skips if projection tables already contain data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectionBackfillService implements CommandLineRunner {

    private final ParseTaskMapper parseTaskMapper;
    private final ParseTaskKnowledgePointMapper parseTaskKnowledgePointMapper;
    private final ParseTaskIdeologyMatchMapper parseTaskIdeologyMatchMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        long existingCount = parseTaskKnowledgePointMapper.selectCount(new LambdaQueryWrapper<>());
        if (existingCount > 0) {
            log.info("Projection tables already have {} rows, skipping backfill.", existingCount);
            return;
        }

        LambdaQueryWrapper<ParseTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(ParseTask::getAiAnalysis);
        List<ParseTask> tasks = parseTaskMapper.selectList(wrapper);
        if (tasks.isEmpty()) {
            log.info("No parse tasks with ai_analysis found, nothing to backfill.");
            return;
        }

        int knowledgePointCount = 0;
        int ideologyMatchCount = 0;
        int failedCount = 0;
        for (ParseTask task : tasks) {
            try {
                PipelineResultDto result = objectMapper.readValue(task.getAiAnalysis(), PipelineResultDto.class);
                if (result == null) {
                    continue;
                }
                Long taskId = task.getId();
                Long courseId = task.getCourseId();
                String version = result.getSchemaVersion();
                if (version == null || version.isBlank()) {
                    version = "v1";
                }

                if (result.getKnowledgePoints() != null) {
                    for (KnowledgePointDto dto : result.getKnowledgePoints()) {
                        ParseTaskKnowledgePoint point = new ParseTaskKnowledgePoint();
                        point.setParseTaskId(taskId);
                        point.setCourseId(courseId);
                        point.setPointName(dto.getPointName());
                        point.setDefinition(dto.getDefinition());
                        point.setChapter(dto.getChapter());
                        point.setEvidenceSnippet(dto.getEvidenceSnippet());
                        point.setPipelineVersion(version);
                        parseTaskKnowledgePointMapper.insert(point);
                        knowledgePointCount++;
                    }
                }

                if (result.getIdeologyMatches() != null) {
                    for (IdeologyMatchDto dto : result.getIdeologyMatches()) {
                        ParseTaskIdeologyMatch match = new ParseTaskIdeologyMatch();
                        match.setParseTaskId(taskId);
                        match.setKnowledgePointName(dto.getKnowledgePointName());
                        match.setIdeologyElement(dto.getIdeologyElement());
                        match.setMatchReason(dto.getMatchReason());
                        match.setPipelineVersion(version);
                        parseTaskIdeologyMatchMapper.insert(match);
                        ideologyMatchCount++;
                    }
                }
            } catch (Exception ex) {
                failedCount++;
                log.warn("Backfill failed for taskId={}: {}", task.getId(), ex.getMessage());
            }
        }

        log.info("Projection backfill completed: tasks={}, knowledgePoints={}, ideologyMatches={}, failed={}",
                tasks.size(), knowledgePointCount, ideologyMatchCount, failedCount);
    }
}
