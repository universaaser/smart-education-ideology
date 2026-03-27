package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.service.KnowledgeService;
import com.smartedu.service.PathRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习路径推荐控制器
 *
 * <p>
 * 基于知识图谱 DAG 结构，为学生生成非线性的个性化学习路径
 *
 * @author SmartEducation Team
 */
@RestController
@RequestMapping("/api/path")
@RequiredArgsConstructor
public class PathRecommendController {

    private final PathRecommendService pathRecommendService;
    private final KnowledgeService knowledgeService;

    /**
     * 获取个性化学习路径推荐
     *
     * <p>
     * 接收学生 ID 和已掌握的节点 ID 列表，
     * 返回推荐学习路径（有序节点 ID + 节点名称 + AI 建议）
     *
     * @param request 包含 studentId / masteredNodeIds / interestTags / maxLength
     * @return 推荐路径结果
     */
    @PostMapping("/recommend")
    public Result<Map<String, Object>> getRecommendedPath(@RequestBody Map<String, Object> request) {
        Long studentId = getLong(request, "studentId", 1L);
        List<Long> masteredNodeIds = getLongList(request, "masteredNodeIds");
        List<String> interestTags = getStringList(request, "interestTags");
        int maxLength = getInt(request, "maxLength", 8);

        // 调用推荐服务，获取有序节点 ID 列表
        Set<Long> masteredSet = masteredNodeIds.stream().collect(Collectors.toSet());
        List<Long> recommendedNodeIds = pathRecommendService.generateLearningPath(
                studentId, masteredSet, interestTags, maxLength);

        // 查询节点名称，构建前端需要的格式
        List<String> nodeNames = new ArrayList<>();
        for (Long nodeId : recommendedNodeIds) {
            KnowledgeNodeView node = knowledgeService.getNodeById(nodeId);
            nodeNames.add(node != null ? node.getName() : "节点" + nodeId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodeIds", recommendedNodeIds);
        result.put("nodeNames", nodeNames);

        return Result.success(result);
    }

    // ============ 请求体字段提取辅助方法 ============

    private List<Long> getLongList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Number)
                    .map(item -> ((Number) item).longValue())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private Long getLong(Map<String, Object> map, String key, Long defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.longValue();
        }
        return defaultVal;
    }

    private int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return defaultVal;
    }
}
