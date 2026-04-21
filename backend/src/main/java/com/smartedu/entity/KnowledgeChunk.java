package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 轻量 RAG 检索片段。
 *
 * <p>
 * 当前阶段只做 chunk 级全文检索，不保存 embedding，避免把轻量增强扩展成完整向量 RAG 工程。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_chunks")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceType;

    private Long sourceId;

    private Integer chunkIndex;

    private String title;

    private String content;

    private String source;

    private String sourceUrl;

    private Long courseId;

    private String knowledgePointName;

    private String ideologyElement;

    @TableField(exist = false)
    private Double searchScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
