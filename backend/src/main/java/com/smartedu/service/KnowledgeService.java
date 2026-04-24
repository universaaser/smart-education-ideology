package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.smartedu.common.PageResult;
import com.smartedu.constant.KnowledgeRelationTypes;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.entity.CourseSubjectKnowledge;
import com.smartedu.entity.IdeologyKnowledge;
import com.smartedu.entity.KnowledgeRelation;
import com.smartedu.entity.StudentActivity;
import com.smartedu.entity.SubjectIdeologyMatch;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.SubjectKnowledgeSource;
import com.smartedu.entity.TeachingMaterialTrace;
import com.smartedu.mapper.CourseSubjectKnowledgeMapper;
import com.smartedu.mapper.IdeologyKnowledgeMapper;
import com.smartedu.mapper.KnowledgeRelationMapper;
import com.smartedu.mapper.StudentActivityMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.SubjectKnowledgeSourceMapper;
import com.smartedu.mapper.TeachingMaterialTraceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱服务
 *
 * <p>
 * 运行时主模型改为学科知识表和思政知识表的组合视图。
 */
@Service
public class KnowledgeService {

    private static final double GRAPH_GRID_X = 240D;
    private static final double GRAPH_GRID_Y = 180D;
    private static final double NODE_LABEL_LINE_HEIGHT = 16D;

    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final IdeologyKnowledgeMapper ideologyKnowledgeMapper;
    private final SubjectIdeologyMatchMapper subjectIdeologyMatchMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final KnowledgeChunkService knowledgeChunkService;
    private final CourseSubjectKnowledgeMapper courseSubjectKnowledgeMapper;
    private final SubjectKnowledgeSourceMapper subjectKnowledgeSourceMapper;
    private final TeachingMaterialTraceMapper teachingMaterialTraceMapper;
    private final StudentActivityMapper studentActivityMapper;

    public KnowledgeService(
            SubjectKnowledgeMapper subjectKnowledgeMapper,
            IdeologyKnowledgeMapper ideologyKnowledgeMapper,
            SubjectIdeologyMatchMapper subjectIdeologyMatchMapper,
            KnowledgeRelationMapper knowledgeRelationMapper,
            KnowledgeChunkService knowledgeChunkService,
            CourseSubjectKnowledgeMapper courseSubjectKnowledgeMapper,
            SubjectKnowledgeSourceMapper subjectKnowledgeSourceMapper,
            TeachingMaterialTraceMapper teachingMaterialTraceMapper,
            StudentActivityMapper studentActivityMapper) {
        this.subjectKnowledgeMapper = subjectKnowledgeMapper;
        this.ideologyKnowledgeMapper = ideologyKnowledgeMapper;
        this.subjectIdeologyMatchMapper = subjectIdeologyMatchMapper;
        this.knowledgeRelationMapper = knowledgeRelationMapper;
        this.knowledgeChunkService = knowledgeChunkService;
        this.courseSubjectKnowledgeMapper = courseSubjectKnowledgeMapper;
        this.subjectKnowledgeSourceMapper = subjectKnowledgeSourceMapper;
        this.teachingMaterialTraceMapper = teachingMaterialTraceMapper;
        this.studentActivityMapper = studentActivityMapper;
    }

    public List<KnowledgeNodeView> getAllNodes() {
        List<KnowledgeNodeView> nodes = new ArrayList<>();

        LambdaQueryWrapper<SubjectKnowledge> subjectWrapper = new LambdaQueryWrapper<>();
        subjectWrapper.orderByAsc(SubjectKnowledge::getId);
        for (SubjectKnowledge subject : subjectKnowledgeMapper.selectList(subjectWrapper)) {
            nodes.add(KnowledgeViewMapper.toSubjectNode(subject));
        }

        LambdaQueryWrapper<IdeologyKnowledge> ideologyWrapper = new LambdaQueryWrapper<>();
        ideologyWrapper.orderByAsc(IdeologyKnowledge::getSortOrder).orderByAsc(IdeologyKnowledge::getId);
        for (IdeologyKnowledge ideology : ideologyKnowledgeMapper.selectList(ideologyWrapper)) {
            nodes.add(KnowledgeViewMapper.toIdeologyNode(ideology));
        }
        return nodes;
    }

    public List<KnowledgeRelation> getAllConnections() {
        List<KnowledgeRelation> relations = new ArrayList<>();

        LambdaQueryWrapper<KnowledgeRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.orderByAsc(KnowledgeRelation::getId);
        for (KnowledgeRelation relation : knowledgeRelationMapper.selectList(relationWrapper)) {
            relation.setRelationType(KnowledgeRelationTypes.normalize(relation.getRelationType()));
            if (relation.getLineStyle() == null || relation.getLineStyle().isBlank()) {
                relation.setLineStyle(KnowledgeRelationTypes.defaultLineStyle(relation.getRelationType()));
            }
            relations.add(relation);
        }

        LambdaQueryWrapper<SubjectIdeologyMatch> matchWrapper = new LambdaQueryWrapper<>();
        matchWrapper.orderByDesc(SubjectIdeologyMatch::getIsPrimary)
                .orderByDesc(SubjectIdeologyMatch::getMatchScore);
        for (SubjectIdeologyMatch match : subjectIdeologyMatchMapper.selectList(matchWrapper)) {
            KnowledgeRelation relation = new KnowledgeRelation();
            // Synthetic negative relation ids keep the graph payload compatible while making it
            // clear these links come from subject_ideology_matches instead of knowledge_relations.
            relation.setId(-match.getId());
            relation.setFromNodeId(KnowledgeViewMapper.toSubjectGraphId(match.getSubjectKnowledgeId()));
            relation.setToNodeId(KnowledgeViewMapper.toIdeologyGraphId(match.getIdeologyKnowledgeId()));
            relation.setRelationType(KnowledgeRelationTypes.VALUE_SHOW);
            relation.setLineStyle("DASHED");
            relation.setWeight(match.getMatchScore() == null ? 1D : match.getMatchScore().doubleValue());
            relation.setDescription(match.getMatchReason());
            relations.add(relation);
        }
        return relations;
    }

    public Map<String, Object> getGraphData() {
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", getAllNodes());
        result.put("relations", getAllConnections());
        return result;
    }

    public KnowledgeNodeView getNodeById(Long id) {
        if (KnowledgeViewMapper.isIdeologyGraphId(id)) {
            IdeologyKnowledge ideology = ideologyKnowledgeMapper.selectById(KnowledgeViewMapper.toEntityId(id));
            return ideology == null ? null : KnowledgeViewMapper.toIdeologyNode(ideology);
        }

        SubjectKnowledge subject = subjectKnowledgeMapper.selectById(id);
        return subject == null ? null : KnowledgeViewMapper.toSubjectNode(subject);
    }

    /**
     * 当前新增节点只支持学科知识，为后续新增学科知识功能预留。
     */
    @Transactional
    public KnowledgeNodeView createNode(KnowledgeNodeView node) {
        String normalizedNodeSize = normalizeNodeSize(node.getNodeSize());
        GraphPosition createPosition = resolveCreatePosition(
                node.getName(),
                normalizedNodeSize,
                node.getPositionX(),
                node.getPositionY());
        SubjectKnowledge subject = new SubjectKnowledge();
        subject.setName(node.getName());
        subject.setSubject(node.getSubject() == null || node.getSubject().isBlank() ? "通用学科" : node.getSubject());
        subject.setCategory(node.getCategory());
        subject.setSummary(node.getTechnicalDefinition());
        subject.setIdeologySummary(node.getIdeologicalValue());
        subject.setPositionX(createPosition.x);
        subject.setPositionY(createPosition.y);
        subject.setNodeSize(normalizedNodeSize);
        subject.setIcon(node.getIcon());
        subject.setTag(node.getCategory());
        subject.setSubTitle(node.getSubTitle());
        subject.setCreatedAt(LocalDateTime.now());
        subject.setUpdatedAt(LocalDateTime.now());
        subjectKnowledgeMapper.insert(subject);
        knowledgeChunkService.refreshSubjectKnowledgeChunks(subject);
        return KnowledgeViewMapper.toSubjectNode(subject);
    }

    @Transactional
    public KnowledgeNodeView updateNode(KnowledgeNodeView node) {
        if (KnowledgeViewMapper.isIdeologyGraphId(node.getId())) {
            // Ideology nodes are fixed dictionary data in this stage, so the editor should
            // treat them as read-only and simply return the existing view object.
            return getNodeById(node.getId());
        }

        SubjectKnowledge subject = subjectKnowledgeMapper.selectById(node.getId());
        if (subject == null) {
            return null;
        }

        subject.setName(node.getName());
        subject.setSubject(node.getSubject());
        subject.setCategory(node.getCategory());
        subject.setSummary(node.getTechnicalDefinition());
        subject.setIdeologySummary(node.getIdeologicalValue());
        subject.setPositionX(node.getPositionX());
        subject.setPositionY(node.getPositionY());
        subject.setNodeSize(normalizeNodeSize(node.getNodeSize()));
        subject.setIcon(node.getIcon());
        subject.setSubTitle(node.getSubTitle());
        subject.setUpdatedAt(LocalDateTime.now());
        subjectKnowledgeMapper.updateById(subject);
        knowledgeChunkService.refreshSubjectKnowledgeChunks(subject);
        return KnowledgeViewMapper.toSubjectNode(subject);
    }

    @Transactional
    public void deleteNode(Long id) {
        if (KnowledgeViewMapper.isIdeologyGraphId(id)) {
            return;
        }

        LambdaQueryWrapper<KnowledgeRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(KnowledgeRelation::getFromNodeId, id)
                .or()
                .eq(KnowledgeRelation::getToNodeId, id);
        knowledgeRelationMapper.delete(relationWrapper);

        LambdaQueryWrapper<SubjectIdeologyMatch> matchWrapper = new LambdaQueryWrapper<>();
        matchWrapper.eq(SubjectIdeologyMatch::getSubjectKnowledgeId, id);
        subjectIdeologyMatchMapper.delete(matchWrapper);

        LambdaQueryWrapper<CourseSubjectKnowledge> courseSubjectWrapper = new LambdaQueryWrapper<>();
        courseSubjectWrapper.eq(CourseSubjectKnowledge::getSubjectKnowledgeId, id);
        courseSubjectKnowledgeMapper.delete(courseSubjectWrapper);

        LambdaQueryWrapper<SubjectKnowledgeSource> sourceWrapper = new LambdaQueryWrapper<>();
        sourceWrapper.eq(SubjectKnowledgeSource::getSubjectKnowledgeId, id);
        subjectKnowledgeSourceMapper.delete(sourceWrapper);

        // Keep historical teaching traces and learning records, but detach the deleted node id
        // so runtime databases with stricter constraints do not fail the delete operation.
        UpdateWrapper<TeachingMaterialTrace> traceWrapper = new UpdateWrapper<>();
        traceWrapper.eq("knowledge_point_id", id)
                .set("knowledge_point_id", null);
        teachingMaterialTraceMapper.update(null, traceWrapper);

        UpdateWrapper<StudentActivity> activityWrapper = new UpdateWrapper<>();
        activityWrapper.eq("knowledge_point_id", id)
                .set("knowledge_point_id", null);
        studentActivityMapper.update(null, activityWrapper);

        subjectKnowledgeMapper.deleteById(id);
        knowledgeChunkService.deleteChunks("SUBJECT_KNOWLEDGE", id);
    }

    @Transactional
    public KnowledgeRelation createRelation(KnowledgeRelation relation) {
        if (KnowledgeViewMapper.isIdeologyGraphId(relation.getFromNodeId())
                || KnowledgeViewMapper.isIdeologyGraphId(relation.getToNodeId())) {
            throw new IllegalArgumentException("当前仅支持学科知识之间创建关系");
        }

        relation.setRelationType(KnowledgeRelationTypes.normalize(relation.getRelationType()));
        if (relation.getLineStyle() == null || relation.getLineStyle().isBlank()) {
            relation.setLineStyle(KnowledgeRelationTypes.defaultLineStyle(relation.getRelationType()));
        }
        relation.setCreatedAt(LocalDateTime.now());
        relation.setUpdatedAt(LocalDateTime.now());
        knowledgeRelationMapper.insert(relation);
        return relation;
    }

    @Transactional
    public void deleteRelation(Long id) {
        if (id != null && id < 0) {
            return;
        }
        knowledgeRelationMapper.deleteById(id);
    }

    public List<KnowledgeNodeView> searchNodes(String keyword) {
        List<KnowledgeNodeView> results = new ArrayList<>();
        results.addAll(searchSubjectNodes(keyword));
        results.addAll(searchIdeologyNodes(keyword));
        return results;
    }

    public PageResult<KnowledgeNodeView> getNodesPage(int page, int size, String category) {
        List<KnowledgeNodeView> allNodes = getAllNodes();
        List<KnowledgeNodeView> filtered = new ArrayList<>();
        for (KnowledgeNodeView node : allNodes) {
            if (category == null || category.isBlank() || category.equals(node.getCategory())) {
                filtered.add(node);
            }
        }

        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int fromIndex = Math.min((safePage - 1) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<KnowledgeNodeView> pageRecords = filtered.subList(fromIndex, toIndex);
        return new PageResult<>(pageRecords, (long) filtered.size(), (long) safeSize, (long) safePage);
    }

    @Transactional
    public void updateNodePosition(Long id, Double x, Double y) {
        if (KnowledgeViewMapper.isIdeologyGraphId(id)) {
            IdeologyKnowledge ideology = ideologyKnowledgeMapper.selectById(KnowledgeViewMapper.toEntityId(id));
            if (ideology != null) {
                ideology.setPositionX(x);
                ideology.setPositionY(y);
                ideology.setUpdatedAt(LocalDateTime.now());
                ideologyKnowledgeMapper.updateById(ideology);
            }
            return;
        }

        SubjectKnowledge subject = subjectKnowledgeMapper.selectById(id);
        if (subject != null) {
            subject.setPositionX(x);
            subject.setPositionY(y);
            subject.setUpdatedAt(LocalDateTime.now());
            subjectKnowledgeMapper.updateById(subject);
        }
    }

    private List<KnowledgeNodeView> searchSubjectNodes(String keyword) {
        LambdaQueryWrapper<SubjectKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(SubjectKnowledge::getName, keyword)
                .or()
                .like(SubjectKnowledge::getSubTitle, keyword)
                .or()
                .like(SubjectKnowledge::getTag, keyword)
                .or()
                .like(SubjectKnowledge::getSummary, keyword);

        List<KnowledgeNodeView> nodes = new ArrayList<>();
        for (SubjectKnowledge subject : subjectKnowledgeMapper.selectList(wrapper)) {
            nodes.add(KnowledgeViewMapper.toSubjectNode(subject));
        }
        return nodes;
    }

    private List<KnowledgeNodeView> searchIdeologyNodes(String keyword) {
        LambdaQueryWrapper<IdeologyKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(IdeologyKnowledge::getName, keyword)
                .or()
                .like(IdeologyKnowledge::getDescription, keyword)
                .or()
                .like(IdeologyKnowledge::getKeywords, keyword);

        List<KnowledgeNodeView> nodes = new ArrayList<>();
        for (IdeologyKnowledge ideology : ideologyKnowledgeMapper.selectList(wrapper)) {
            nodes.add(KnowledgeViewMapper.toIdeologyNode(ideology));
        }
        return nodes;
    }

    private GraphPosition resolveCreatePosition(String nodeName, String nodeSize, Double requestedX, Double requestedY) {
        if (requestedX != null && requestedY != null) {
            return new GraphPosition(requestedX, requestedY);
        }

        List<KnowledgeNodeView> existingNodes = getAllNodes();
        if (existingNodes.isEmpty()) {
            return new GraphPosition(0D, 0D);
        }

        for (int layer = 0; layer <= 12; layer++) {
            for (GraphPosition candidate : buildCandidatePositions(layer)) {
                if (!hasNodeOverlap(candidate, nodeName, nodeSize, existingNodes)) {
                    return candidate;
                }
            }
        }

        double fallbackY = existingNodes.stream()
                .map(KnowledgeNodeView::getPositionY)
                .filter(value -> value != null)
                .max(Double::compareTo)
                .orElse(0D) + GRAPH_GRID_Y;
        return new GraphPosition(0D, fallbackY);
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
            List<KnowledgeNodeView> existingNodes) {
        double candidateWidth = estimateNodeFootprintWidth(candidateName, candidateNodeSize);
        double candidateHeight = estimateNodeFootprintHeight(candidateName, candidateNodeSize);
        for (KnowledgeNodeView existingNode : existingNodes) {
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
}
