package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统动态实体类
 * 
 * <p>
 * 对应数据库表：system_activities
 * <p>
 * 记录系统动态消息
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("system_activities")
public class SystemActivity {

    /**
     * 动态ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联用户ID
     */
    private Long userId;

    /**
     * 动态类型：AI_ANALYSIS/UPLOAD/ALERT/GRAPH_UPDATE
     */
    private String activityType;

    /**
     * 动态标题
     */
    private String title;

    /**
     * 动态描述
     */
    private String description;

    /**
     * 图标名称
     */
    private String icon;

    /**
     * 关联对象ID
     */
    private Long referenceId;

    /**
     * 关联对象类型
     */
    private String referenceType;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
