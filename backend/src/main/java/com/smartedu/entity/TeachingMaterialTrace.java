package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Structured trace rows for teaching materials.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("teaching_material_traces")
public class TeachingMaterialTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;

    private Long parseTaskId;

    private Long courseId;

    private Long knowledgePointId;

    private String knowledgePointName;

    private String ideologyElement;

    private String evidenceSnippet;

    private String matchReason;

    private LocalDateTime createdAt;
}
