-- =====================================================
-- selection explanation history migration
-- =====================================================

CREATE TABLE IF NOT EXISTS selection_explain_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'record id',
    user_id BIGINT NOT NULL COMMENT 'user id',
    course_id BIGINT NULL COMMENT 'course id',
    material_id BIGINT NULL COMMENT 'teaching material id',
    parse_task_id BIGINT NULL COMMENT 'parse task id',
    selected_text TEXT NOT NULL COMMENT 'selected text',
    answer TEXT NOT NULL COMMENT 'final explanation',
    model_reasoning TEXT NULL COMMENT 'model reasoning text',
    evidence_json TEXT NULL COMMENT 'knowledge evidence json',
    has_reliable_evidence TINYINT NOT NULL DEFAULT 0 COMMENT 'whether knowledge-base evidence exists',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',

    INDEX idx_ser_user (user_id),
    INDEX idx_ser_course (course_id),
    INDEX idx_ser_material (material_id),
    INDEX idx_ser_task (parse_task_id),
    INDEX idx_ser_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='selection explanation records';
