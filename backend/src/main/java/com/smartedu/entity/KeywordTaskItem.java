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
@TableName("keyword_task_items")
public class KeywordTaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String keyword;

    private String title;

    private String sourceUrl;

    private String excerpt;

    private String aiSummary;

    private String ideologyTags;

    private String status;

    private Long resourceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
