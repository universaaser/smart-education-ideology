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
 * Persisted selected-text explanation record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("selection_explain_records")
public class SelectionExplainRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long courseId;

    private Long materialId;

    private Long parseTaskId;

    private String selectedText;

    private String answer;

    private String modelReasoning;

    private String evidenceJson;

    private Integer hasReliableEvidence;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
