package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学科知识与思政知识匹配实体
 *
 * <p>
 * 用于表达一个学科点可映射多个思政种类，并保留主匹配和匹配理由。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("subject_ideology_matches")
public class SubjectIdeologyMatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long subjectKnowledgeId;

    private Long ideologyKnowledgeId;

    private Integer isPrimary;

    private BigDecimal matchScore;

    private String matchReason;

    private String reviewStatus;

    private Integer version;

    private Long reviewerId;

    private LocalDateTime reviewedAt;

    private String reviewComment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
