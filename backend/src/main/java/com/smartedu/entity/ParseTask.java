package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档解析任务实体类
 * 
 * <p>
 * 对应数据库表：parse_tasks
 * <p>
 * 记录文档解析任务状态
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("parse_tasks")
public class ParseTask {

    /**
     * 任务ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 任务状态：PENDING/UPLOADING/PARSING/ANALYZING/COMPLETED/FAILED
     */
    private String status;

    /**
     * 处理进度（百分比）
     */
    private Integer progress;

    /**
     * 当前处理步骤描述
     */
    private String currentStep;

    /**
     * MinerU解析后的结构化文本
     */
    private String parsedContent;

    /**
     * AI分析结果（思政融合建议）
     */
    private String aiAnalysis;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 开始处理时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
