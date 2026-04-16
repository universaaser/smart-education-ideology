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
 * Teaching material entity.
 *
 * <p>
 * This table stores editable teaching outputs generated from upload pipeline.
 * It keeps draft and published versions for the same parse task.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("teaching_materials")
public class TeachingMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parseTaskId;

    private Long userId;

    private Long courseId;

    private String title;

    private String lectureNotes;

    private String casesJson;

    private String questionsJson;

    private String documentStructureJson;

    private String knowledgePointsJson;

    private String ideologyMatchesJson;

    private String traceJson;

    private String schemaVersion;

    private Integer versionNo;

    private Integer isLatest;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
