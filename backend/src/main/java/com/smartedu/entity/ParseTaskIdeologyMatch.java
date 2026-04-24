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
 * Parse task ideology match projection entity.
 *
 * <p>
 * Flattens pipeline result ideologyMatches into a queryable relational table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("parse_task_ideology_matches")
public class ParseTaskIdeologyMatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parseTaskId;

    private String knowledgePointName;

    private String ideologyElement;

    private String matchReason;

    private String citationExplanation;

    private String resourceCitationsJson;

    private String pipelineVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
