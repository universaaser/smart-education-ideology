package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI对话消息实体类
 * 
 * <p>
 * 对应数据库表：chat_messages
 * <p>
 * 存储AI助手对话消息内容
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_messages")
public class ChatMessage {

    /**
     * 消息ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 角色：USER/ASSISTANT/SYSTEM
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 内容类型：TEXT/HTML/MARKDOWN
     */
    private String contentType;

    /**
     * 消耗的Token数
     */
    private Integer tokensUsed;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
