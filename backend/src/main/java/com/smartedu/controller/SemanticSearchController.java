package com.smartedu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.common.Result;
import com.smartedu.dto.SemanticHitDto;
import com.smartedu.dto.SemanticSearchRequestDto;
import com.smartedu.entity.ParseTaskIdeologyMatch;
import com.smartedu.entity.ParseTaskKnowledgePoint;
import com.smartedu.mapper.ParseTaskIdeologyMatchMapper;
import com.smartedu.mapper.ParseTaskKnowledgePointMapper;
import com.smartedu.service.VectorIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Phase 2 + Phase 3A: projection table query and semantic search controller.
 */
@RestController
@RequestMapping("/api/semantic")
@RequiredArgsConstructor
public class SemanticSearchController {

    private final VectorIndexService vectorIndexService;
    private final ParseTaskKnowledgePointMapper knowledgePointMapper;
    private final ParseTaskIdeologyMatchMapper ideologyMatchMapper;

    /**
     * Phase 3A: semantic vector search across indexed collections.
     */
    @PostMapping("/search")
    public Result<List<SemanticHitDto>> semanticSearch(@RequestBody SemanticSearchRequestDto request) {
        String collection = vectorIndexService.resolveCollection(request.getScope());
        Map<String, Object> filter = null;
        if (request.getCourseId() != null) {
            Map<String, Object> matchCondition = Map.of("key", "course_id", "match", Map.of("value", request.getCourseId()));
            filter = Map.of("must", List.of(matchCondition));
        }
        List<SemanticHitDto> hits = vectorIndexService.search(collection, request.getText(), request.getTopK(), filter);
        return Result.success(hits);
    }

    /**
     * Phase 2: query knowledge point projections by parse task id.
     */
    @GetMapping("/knowledge-points")
    public Result<List<ParseTaskKnowledgePoint>> getKnowledgePointsByTask(@RequestParam Long taskId) {
        LambdaQueryWrapper<ParseTaskKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParseTaskKnowledgePoint::getParseTaskId, taskId);
        return Result.success(knowledgePointMapper.selectList(wrapper));
    }

    /**
     * Phase 2: query ideology match projections by parse task id.
     */
    @GetMapping("/ideology-matches")
    public Result<List<ParseTaskIdeologyMatch>> getIdeologyMatchesByTask(@RequestParam Long taskId) {
        LambdaQueryWrapper<ParseTaskIdeologyMatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParseTaskIdeologyMatch::getParseTaskId, taskId);
        return Result.success(ideologyMatchMapper.selectList(wrapper));
    }
}
