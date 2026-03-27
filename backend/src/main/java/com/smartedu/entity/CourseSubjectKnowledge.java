package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 课程与学科知识关联实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("course_subject_knowledge")
public class CourseSubjectKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long subjectKnowledgeId;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
