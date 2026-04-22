package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.dto.PathRecommendRequestDto;
import com.smartedu.service.KnowledgeService;
import com.smartedu.service.PathRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Learning-path recommendation controller.
 */
@RestController
@RequestMapping("/api/path")
@RequiredArgsConstructor
public class PathRecommendController {

    private final PathRecommendService pathRecommendService;
    private final KnowledgeService knowledgeService;

    /**
     * Recommend a learning path from a structured request.
     */
    @PostMapping("/recommend")
    public Result<Map<String, Object>> getRecommendedPath(@RequestBody PathRecommendRequestDto request) {
        // Rejecting a missing student id here is safer than reusing an implicit demo account.
        if (request == null || request.getStudentId() == null) {
            return Result.badRequest("Student id cannot be empty");
        }

        List<Long> masteredNodeIds = request.getMasteredNodeIds() == null ? List.of() : request.getMasteredNodeIds();
        List<String> interestTags = request.getInterestTags() == null ? List.of() : request.getInterestTags();
        int maxLength = request.getMaxLength() == null ? 8 : request.getMaxLength();

        Set<Long> masteredSet = masteredNodeIds.stream().collect(Collectors.toSet());
        List<Long> recommendedNodeIds = pathRecommendService.generateLearningPath(
                request.getStudentId(),
                masteredSet,
                interestTags,
                maxLength);

        List<String> nodeNames = new ArrayList<>();
        for (Long nodeId : recommendedNodeIds) {
            KnowledgeNodeView node = knowledgeService.getNodeById(nodeId);
            nodeNames.add(node != null ? node.getName() : "Node " + nodeId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodeIds", recommendedNodeIds);
        result.put("nodeNames", nodeNames);
        return Result.success(result);
    }
}
