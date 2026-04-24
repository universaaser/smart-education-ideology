package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.constant.KnowledgeRelationTypes;
import com.smartedu.entity.KnowledgeRelation;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.mapper.KnowledgeRelationMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 个性化学习路径推荐服务
 * 
 * <p>
 * 基于知识图谱 DAG 结构，生成非线性的个性化学习路径
 * 
 * @author SmartEducation Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PathRecommendService {

    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final AiIntelligenceService aiIntelligenceService;

    /**
     * 生成个性化学习路径
     * 
     * <p>
     * 根据学生当前的知识掌握度和兴趣偏好，
     * 使用知识图谱的 DAG 结构生成跳跃式的非线性学习路径
     * 
     * @param studentId       学生ID
     * @param masteredNodeIds 已掌握的知识点ID列表
     * @param interestTags    兴趣标签
     * @param maxLength       路径最大长度
     * @return 推荐的知识点ID列表（学习顺序）
     */
    public List<Long> generateLearningPath(
            Long studentId,
            Set<Long> masteredNodeIds,
            List<String> interestTags,
            int maxLength) {

        // 1. 获取所有知识点和关系
        List<SubjectKnowledge> allNodes = subjectKnowledgeMapper.selectList(null);
        List<KnowledgeRelation> allRelations = knowledgeRelationMapper.selectList(null);

        // 2. 构建邻接表（DAG 结构）
        Map<Long, List<Long>> adjacencyList = buildAdjacencyList(allRelations);

        // 3. 计算每个节点的优先级分数
        Map<Long, Double> priorityScores = calculatePriorityScores(
                allNodes, masteredNodeIds, interestTags);

        // 4. 使用改进的 BFS/DFS 生成路径
        List<Long> recommendedPath = new ArrayList<>();
        Set<Long> visited = new HashSet<>(masteredNodeIds);

        // 找到起始节点（已掌握节点的直接后继中优先级最高的）
        PriorityQueue<Long> candidates = new PriorityQueue<>(
                (a, b) -> Double.compare(priorityScores.getOrDefault(b, 0.0),
                        priorityScores.getOrDefault(a, 0.0)));

        // 初始化候选节点
        for (Long masteredId : masteredNodeIds) {
            List<Long> successors = adjacencyList.getOrDefault(masteredId, Collections.emptyList());
            for (Long successor : successors) {
                if (!visited.contains(successor)) {
                    candidates.add(successor);
                }
            }
        }

        // 如果没有已掌握节点，从根节点开始
        if (candidates.isEmpty()) {
            Set<Long> hasIncoming = new HashSet<>();
            for (KnowledgeRelation relation : allRelations) {
                hasIncoming.add(relation.getToNodeId());
            }
            for (SubjectKnowledge node : allNodes) {
                if (!hasIncoming.contains(node.getId()) && !visited.contains(node.getId())) {
                    candidates.add(node.getId());
                }
            }
        }

        // 5. 生成路径
        while (!candidates.isEmpty() && recommendedPath.size() < maxLength) {
            Long nextNode = candidates.poll();

            if (visited.contains(nextNode)) {
                continue;
            }

            // 检查前置依赖是否满足
            if (checkPrerequisites(nextNode, visited, allRelations)) {
                recommendedPath.add(nextNode);
                visited.add(nextNode);

                // 添加后继节点到候选
                List<Long> successors = adjacencyList.getOrDefault(nextNode, Collections.emptyList());
                for (Long successor : successors) {
                    if (!visited.contains(successor)) {
                        candidates.add(successor);
                    }
                }
            }
        }

        log.info("为学生 {} 生成学习路径，包含 {} 个知识点", studentId, recommendedPath.size());

        return recommendedPath;
    }

    /**
     * 构建邻接表
     */
    private Map<Long, List<Long>> buildAdjacencyList(List<KnowledgeRelation> relations) {
        Map<Long, List<Long>> adjacencyList = new HashMap<>();
        for (KnowledgeRelation relation : relations) {
            adjacencyList
                    .computeIfAbsent(relation.getFromNodeId(), k -> new ArrayList<>())
                    .add(relation.getToNodeId());
        }
        return adjacencyList;
    }

    /**
     * 计算节点优先级分数
     * 
     * <p>
     * 考虑因素：
     * <ul>
     * <li>与兴趣标签的匹配度</li>
     * <li>节点的重要性（连接数）</li>
     * <li>思政价值含量</li>
     * </ul>
     */
    private Map<Long, Double> calculatePriorityScores(
            List<SubjectKnowledge> nodes,
            Set<Long> masteredNodeIds,
            List<String> interestTags) {

        Map<Long, Double> scores = new HashMap<>();

        for (SubjectKnowledge node : nodes) {
            if (masteredNodeIds.contains(node.getId())) {
                scores.put(node.getId(), 0.0);
                continue;
            }

            double score = 1.0;

            // 兴趣匹配加分
            if (interestTags != null && node.getCategory() != null) {
                for (String tag : interestTags) {
                    if (node.getCategory().contains(tag) ||
                            (node.getTag() != null && node.getTag().contains(tag))) {
                        score += 0.5;
                    }
                }
            }

            // 有思政匹配摘要的学科知识适当加分
            if (node.getIdeologySummary() != null && !node.getIdeologySummary().isEmpty()) {
                score += 0.2;
            }

            scores.put(node.getId(), score);
        }

        return scores;
    }

    /**
     * 检查前置依赖是否满足
     */
    private boolean checkPrerequisites(
            Long nodeId,
            Set<Long> mastered,
            List<KnowledgeRelation> allRelations) {

        // 找到所有指向该节点的"技术基础"关系
        for (KnowledgeRelation relation : allRelations) {
            if (relation.getToNodeId().equals(nodeId) &&
                    KnowledgeRelationTypes.TECH_BASE.equals(
                            KnowledgeRelationTypes.normalize(relation.getRelationType()))) {
                // 前置节点必须已掌握
                if (!mastered.contains(relation.getFromNodeId())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 使用 AI 辅助优化学习路径
     * 
     * @param path           初始路径
     * @param studentProfile 学生画像
     * @return AI 优化后的学习建议
     */
    public String getAiPathSuggestion(List<Long> path, String studentProfile) {
        // 获取路径上的知识点名称
        List<String> nodeNames = new ArrayList<>();
        for (Long nodeId : path) {
            SubjectKnowledge node = subjectKnowledgeMapper.selectById(nodeId);
            if (node != null) {
                nodeNames.add(node.getName());
            }
        }

        String prompt = String.format(
                "学生画像：%s\n推荐学习路径：%s\n\n请分析这个学习路径的合理性，并给出优化建议。",
                studentProfile,
                String.join(" → ", nodeNames));

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        return aiIntelligenceService.chatForTask(AiIntelligenceService.TASK_PATH, messages,
                "你是一位教育专家，擅长个性化学习路径规划。");
    }
}
