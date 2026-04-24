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
 * Parse task correction snapshot.
 *
 * <p>
 * Keeps the latest manually corrected structured result for one parse task.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("parse_task_corrections")
public class ParseTaskCorrection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parseTaskId;

    private String correctedResultJson;

    private LocalDateTime sourceCompletedAt;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
