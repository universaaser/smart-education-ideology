package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学科知识来源追溯实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("subject_knowledge_sources")
public class SubjectKnowledgeSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long subjectKnowledgeId;

    private Long resourceId;

    private String sourceType;

    private String sourceUrl;

    private String excerpt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
