package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Parse task knowledge point projection entity.
 *
 * <p>
 * Flattens pipeline result knowledgePoints into a queryable relational table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("parse_task_knowledge_points")
public class ParseTaskKnowledgePoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parseTaskId;

    private Long courseId;

    private String pointName;

    private String definition;

    private String chapter;

    private String evidenceSnippet;

    private String resourceCitationsJson;

    private String pipelineVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
