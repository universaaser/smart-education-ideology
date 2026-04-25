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

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("subject_ideology_match_reviews")
public class SubjectIdeologyMatchReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long matchId;

    private String action;

    private String previousStatus;

    private String nextStatus;

    private String previousReason;

    private String nextReason;

    private Long reviewerId;

    private String reviewComment;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
