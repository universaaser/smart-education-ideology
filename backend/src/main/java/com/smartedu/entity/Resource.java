package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资源实体类
 * 
 * <p>
 * 对应数据库表：resources
 * <p>
 * 存储教学资源及其思政分析结果
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("resources")
public class Resource {

    /**
     * 资源ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 资源标题
     */
    private String title;

    /**
     * 来源出处
     */
    private String source;

    /**
     * 来源链接（用于去重与追溯）
     */
    private String sourceUrl;

    /**
     * 学科分类
     */
    private String category;

    /**
     * 知识点原文内容
     */
    private String content;

    /**
     * LLM提取的思政价值总结
     */
    private String ideologySummary;

    /**
     * 思政标签（JSON数组）
     */
    private String tags;

    /**
     * 文件存储路径
     */
    private String filePath;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 同步状态：PENDING/PROCESSING/SYNCED/FAILED
     */
    private String syncStatus;

    private String reviewStatus;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    /**
     * 关联的解析任务ID
     */
    private Long parseTaskId;

    /**
     * 创建者ID
     */
    private Long creatorId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    // 数据库已配置 ON UPDATE CURRENT_TIMESTAMP，不需要应用层 fill
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;
}

