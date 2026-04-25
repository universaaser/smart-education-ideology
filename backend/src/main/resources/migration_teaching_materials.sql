-- =====================================================
-- teaching_materials migration
-- Purpose: persist editable teacher materials generated from upload pipeline
-- =====================================================

CREATE TABLE IF NOT EXISTS teaching_materials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'material id',
    parse_task_id BIGINT NOT NULL COMMENT 'related parse task id',
    user_id BIGINT NOT NULL COMMENT 'teacher user id',
    chapter_id BIGINT NULL COMMENT 'optional course chapter id',
    title VARCHAR(300) NOT NULL COMMENT 'material title',
    lecture_notes TEXT COMMENT 'editable lecture notes',
    cases_json TEXT COMMENT 'editable cases in json array',
    questions_json TEXT COMMENT 'editable questions in json array',
    document_structure_json TEXT COMMENT 'snapshot of document structure json',
    knowledge_points_json TEXT COMMENT 'snapshot of knowledge points json',
    ideology_matches_json TEXT COMMENT 'snapshot of ideology match json',
    trace_json TEXT COMMENT 'trace information json',
    schema_version VARCHAR(32) DEFAULT 'v1' COMMENT 'pipeline schema version',
    version_no INT NOT NULL DEFAULT 1 COMMENT 'material version number',
    is_latest TINYINT NOT NULL DEFAULT 1 COMMENT 'latest flag 1 yes 0 no',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT or PUBLISHED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'soft delete flag',

    INDEX idx_tm_task_latest (parse_task_id, is_latest),
    INDEX idx_tm_task_version (parse_task_id, version_no),
    INDEX idx_tm_user (user_id),
    INDEX idx_tm_chapter (chapter_id),
    INDEX idx_tm_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='teaching materials table';
