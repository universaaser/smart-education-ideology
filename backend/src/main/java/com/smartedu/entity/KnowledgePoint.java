package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识点实体类
 * 
 * <p>
 * 对应数据库表：knowledge_points
 * <p>
 * 存储专业知识点及其思政价值映射，用于构建知识图谱的节点
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_points")
public class KnowledgePoint {

    /**
     * 知识点ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 专业术语名称
     */
    private String name;

    /**
     * 所属学科
     */
    private String subject;

    /**
     * 分类（如：物联网、计算机科学等）
     */
    private String category;

    /**
     * 技术定义
     */
    private String technicalDefinition;

    /**
     * 思政价值（AI挖掘结果）
     */
    private String ideologicalValue;

    /**
     * 来源链接
     */
    private String sourceUrl;

    /**
     * 关联文档ID
     */
    private Long documentId;

    /**
     * 节点类型
     * TECH: 技术节点
     * IDEO: 思政节点
     */
    private String nodeType;

    /**
     * 图谱中X坐标
     */
    private Double positionX;

    /**
     * 图谱中Y坐标
     */
    private Double positionY;

    /**
     * 节点大小：LG/MD/SM
     */
    private String nodeSize;

    /**
     * 节点图标
     */
    private String icon;

    /**
     * 节点标签
     */
    private String tag;

    /**
     * 副标题
     */
    private String subTitle;

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
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;
}
