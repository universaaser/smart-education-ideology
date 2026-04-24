-- =====================================================
-- Parse task correction snapshot table
-- =====================================================

CREATE TABLE IF NOT EXISTS parse_task_corrections (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'correction snapshot id',
    parse_task_id BIGINT NOT NULL COMMENT 'related parse task id',
    corrected_result_json JSON NOT NULL COMMENT 'latest corrected structured result json',
    source_completed_at DATETIME NULL COMMENT 'parse task completed_at used as correction source baseline',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or STALE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    UNIQUE KEY uk_ptc_task (parse_task_id),
    INDEX idx_ptc_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='parse task correction snapshots';
