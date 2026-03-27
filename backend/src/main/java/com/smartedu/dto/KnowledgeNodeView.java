package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识节点视图对象
 *
 * <p>
 * 统一前端图谱、课程列表和搜索接口的节点结构，避免前端因为拆表而改协议。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeNodeView {

    private Long id;

    private String name;

    private String category;

    private String technicalDefinition;

    private String ideologicalValue;

    private String subject;

    private String nodeType;

    private Double positionX;

    private Double positionY;

    private String icon;

    private String subTitle;

    private String nodeSize;

    private String description;
}
