package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识点来源追溯实体
 *
 * <p>
 * 记录知识点来自哪条资源或上传任务，供聊天引用和后续“按选中文本解释”使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_point_sources")
public class KnowledgePointSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgePointId;

    private Long resourceId;

    private String sourceType;

    private String sourceUrl;

    private String excerpt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
