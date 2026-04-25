CREATE TABLE IF NOT EXISTS knowledge_change_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'change log id',
    change_type VARCHAR(50) NOT NULL COMMENT 'change type',
    relation_id BIGINT NOT NULL COMMENT 'relation id',
    before_json TEXT COMMENT 'relation snapshot before change',
    after_json TEXT COMMENT 'relation snapshot after change',
    undone TINYINT NOT NULL DEFAULT 0 COMMENT 'undo status: 0-no 1-yes',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    INDEX idx_knowledge_change_relation (relation_id),
    INDEX idx_knowledge_change_undo (undone, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='knowledge graph change log';
