package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一检索上下文项
 *
 * <p>
 * 聊天、解释接口和未来的文本选中解释都共享这类上下文结构，避免每个接口都重复拼接知识库信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeContextItem {

    public KnowledgeContextItem(
            String itemType,
            Long referenceId,
            String title,
            String summary,
            String source,
            String sourceUrl,
            String nodeType) {
        this.itemType = itemType;
        this.referenceId = referenceId;
        this.title = title;
        this.summary = summary;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.nodeType = nodeType;
        this.snippet = summary;
    }

    private String itemType;

    private Long referenceId;

    private String title;

    private String summary;

    private String source;

    private String sourceUrl;

    private String nodeType;

    private String snippet;

    private Double score;

    private String matchedBy;
}
