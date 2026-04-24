package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI对话会话实体类
 * 
 * <p>
 * 对应数据库表：chat_sessions
 * <p>
 * 存储AI助手对话会话
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_sessions")
public class ChatSession {

    /**
     * 会话ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 会话摘要
     */
    private String summary;

    /**
     * 使用的AI模型
     */
    private String aiModel;

    /**
     * 消息数量
     */
    private Integer messageCount;

    /**
     * 最后消息时间
     */
    private LocalDateTime lastMessageAt;

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
