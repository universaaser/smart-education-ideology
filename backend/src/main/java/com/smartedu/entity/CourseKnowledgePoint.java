package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 课程与知识点关联实体
 *
 * <p>
 * 将课程展示从“全量知识点”切换为“显式关联知识点”，为后续路径推荐和课程定制化展示打底。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("course_knowledge_points")
public class CourseKnowledgePoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long knowledgePointId;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
