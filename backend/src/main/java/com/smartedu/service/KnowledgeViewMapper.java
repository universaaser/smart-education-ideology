package com.smartedu.service;

import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.entity.IdeologyKnowledge;
import com.smartedu.entity.SubjectKnowledge;

/**
 * 节点视图映射工具
 *
 * <p>
 * 将拆表后的实体统一映射成前端已经使用的节点结构。
 */
public final class KnowledgeViewMapper {

    private KnowledgeViewMapper() {
    }

    /**
     * 学科知识使用正数 ID，保持与数据库主键一致。
     */
    public static KnowledgeNodeView toSubjectNode(SubjectKnowledge subject) {
        return new KnowledgeNodeView(
                subject.getId(),
                subject.getName(),
                safe(subject.getCategory()),
                safe(subject.getSummary()),
                safe(subject.getIdeologySummary()),
                safe(subject.getSubject()),
                "TECH",
                defaultDouble(subject.getPositionX()),
                defaultDouble(subject.getPositionY()),
                safe(subject.getIcon()),
                safe(subject.getSubTitle()),
                defaultSize(subject.getNodeSize()),
                safe(subject.getSummary()));
    }

    /**
     * 思政知识在图谱中使用负数 ID，避免与学科知识表主键冲突。
     */
    public static KnowledgeNodeView toIdeologyNode(IdeologyKnowledge ideology) {
        return new KnowledgeNodeView(
                -ideology.getId(),
                ideology.getName(),
                "思政",
                safe(ideology.getDescription()),
                safe(ideology.getDescription()),
                "思政教育",
                "IDEO",
                defaultDouble(ideology.getPositionX()),
                defaultDouble(ideology.getPositionY()),
                safe(ideology.getIcon()),
                safe(ideology.getSubTitle()),
                defaultSize(ideology.getNodeSize()),
                safe(ideology.getDescription()));
    }

    public static long toSubjectGraphId(Long subjectId) {
        // Subject knowledge keeps a positive graph id so existing relation storage can be reused.
        return subjectId == null ? 0L : subjectId;
    }

    public static long toIdeologyGraphId(Long ideologyId) {
        // Ideology nodes are projected as negative ids in graph responses to avoid collisions
        // with subject_knowledge primary keys while preserving the current frontend payload shape.
        return ideologyId == null ? 0L : -ideologyId;
    }

    public static boolean isIdeologyGraphId(Long graphId) {
        return graphId != null && graphId < 0;
    }

    public static long toEntityId(Long graphId) {
        // Convert graph ids back to the underlying table id before reading ideology_knowledge.
        return graphId == null ? 0L : Math.abs(graphId);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Double defaultDouble(Double value) {
        return value == null ? 0D : value;
    }

    private static String defaultSize(String value) {
        return value == null || value.isBlank() ? "MD" : value;
    }
}
