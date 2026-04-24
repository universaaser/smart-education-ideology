-- =====================================================
-- course material quality rules
-- =====================================================

CREATE TABLE IF NOT EXISTS course_material_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'rule id',
    course_id BIGINT NOT NULL COMMENT 'course id',
    rule_json JSON NOT NULL COMMENT 'course-level teaching material quality rules',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'soft delete flag',

    INDEX idx_cmr_course_latest (course_id, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='course material quality rules';
