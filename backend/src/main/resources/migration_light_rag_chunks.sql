CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '检索片段ID',
    source_type VARCHAR(50) NOT NULL COMMENT '来源类型：RESOURCE/SUBJECT_KNOWLEDGE',
    source_id BIGINT NOT NULL COMMENT '来源记录ID',
    chunk_index INT NOT NULL COMMENT '来源内片段序号',
    title VARCHAR(300) NOT NULL COMMENT '片段标题',
    content TEXT NOT NULL COMMENT '片段正文',
    source VARCHAR(200) COMMENT '来源名称',
    source_url VARCHAR(500) COMMENT '来源链接',
    course_id BIGINT COMMENT '课程ID',
    knowledge_point_name VARCHAR(200) COMMENT '知识点名称',
    ideology_element VARCHAR(200) COMMENT '思政元素',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    INDEX idx_kc_source (source_type, source_id),
    INDEX idx_kc_course (course_id),
    INDEX idx_kc_knowledge_point (knowledge_point_name),
    FULLTEXT INDEX ft_kc_search (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轻量RAG知识片段表';

INSERT INTO knowledge_chunks (
    source_type,
    source_id,
    chunk_index,
    title,
    content,
    source,
    source_url,
    knowledge_point_name,
    ideology_element,
    created_at,
    updated_at,
    deleted
)
SELECT
    'RESOURCE',
    id,
    0,
    title,
    LEFT(COALESCE(NULLIF(content, ''), ideology_summary), 1200),
    source,
    source_url,
    title,
    COALESCE(tags, category),
    NOW(),
    NOW(),
    0
FROM resources
WHERE deleted = 0
  AND COALESCE(NULLIF(content, ''), ideology_summary) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM knowledge_chunks kc
      WHERE kc.source_type = 'RESOURCE'
        AND kc.source_id = resources.id
        AND kc.deleted = 0
  );

INSERT INTO knowledge_chunks (
    source_type,
    source_id,
    chunk_index,
    title,
    content,
    source,
    source_url,
    knowledge_point_name,
    ideology_element,
    created_at,
    updated_at,
    deleted
)
SELECT
    'SUBJECT_KNOWLEDGE',
    id,
    0,
    name,
    LEFT(COALESCE(NULLIF(summary, ''), ideology_summary), 1200),
    subject,
    source_url,
    name,
    tag,
    NOW(),
    NOW(),
    0
FROM subject_knowledge
WHERE deleted = 0
  AND COALESCE(NULLIF(summary, ''), ideology_summary) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM knowledge_chunks kc
      WHERE kc.source_type = 'SUBJECT_KNOWLEDGE'
        AND kc.source_id = subject_knowledge.id
        AND kc.deleted = 0
  );
