package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识关系实体类
 * 
 * <p>
 * 对应数据库表：knowledge_relations
 * <p>
 * 用于构建知识图谱的边关系
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_relations")
public class KnowledgeRelation {

    /**
     * 关系ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 起始节点ID
     */
    private Long fromNodeId;

    /**
     * 结束节点ID
     */
    private Long toNodeId;

    /**
     * 关系类型：理论支撑、价值体现、技术基础等
     */
    private String relationType;

    /**
     * 连线样式：SOLID/DASHED
     */
    private String lineStyle;

    /**
     * 关系权重
     */
    private Double weight;

    /**
     * 关系描述
     */
    private String description;

    /**
     * 创建者ID
     */
    private Long creatorId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    // 数据库已配置 ON UPDATE CURRENT_TIMESTAMP，不需要应用层 fill
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;
}
