package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学习行为实体类
 * 
 * <p>
 * 对应数据库表：student_activities
 * <p>
 * 记录学生学习行为数据，用于预警分析
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("student_activities")
public class StudentActivity {

    /**
     * 记录ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学生用户ID
     */
    private Long studentId;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 知识点ID
     */
    private Long knowledgePointId;

    /**
     * 学习时长（分钟）
     */
    private Integer studyDuration;

    /**
     * 答题正确率（百分比）
     */
    private BigDecimal correctRate;

    /**
     * 专注度评分（0-100）
     */
    private BigDecimal focusScore;

    /**
     * 情绪状态
     * NORMAL: 正常
     * CONFUSED: 困惑
     * ANGRY: 愤怒
     * DISTRACTED: 分心
     */
    private String emotionStatus;

    /**
     * 预警等级
     * 0: 正常
     * 1: 轻度预警
     * 2: 中度预警
     * 3: 重度预警
     */
    private Integer alertLevel;

    /**
     * 预警消息
     */
    private String alertMessage;

    /**
     * 学习会话开始时间
     */
    private LocalDateTime sessionStart;

    /**
     * 学习会话结束时间
     */
    private LocalDateTime sessionEnd;

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
