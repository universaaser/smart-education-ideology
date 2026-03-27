package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 课程实体类
 * 
 * <p>
 * 对应数据库表：courses
 * <p>
 * 存储课程基本信息
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("courses")
public class Course {

    /**
     * 课程ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 课程名称
     */
    private String name;

    /**
     * 课程编码
     */
    private String code;

    /**
     * 课程描述
     */
    private String description;

    /**
     * 授课教师ID
     */
    private Long teacherId;

    /**
     * 学期（如：2024春）
     */
    private String semester;

    /**
     * 课程进度（百分比）
     */
    private Integer progress;

    /**
     * 思政融合度评级：EXCELLENT/GOOD/FAIR/POOR
     */
    private String ideologyScore;

    /**
     * 课程封面图
     */
    private String coverImage;

    /**
     * 状态：0-草稿 1-发布 2-归档
     */
    private Integer status;

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

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;
}
