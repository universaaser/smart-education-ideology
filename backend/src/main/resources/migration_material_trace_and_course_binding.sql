-- =====================================================
-- material trace + course binding migration
-- =====================================================

ALTER TABLE parse_tasks
    ADD COLUMN IF NOT EXISTS course_id BIGINT NULL COMMENT 'optional course binding id';

ALTER TABLE teaching_materials
    ADD COLUMN IF NOT EXISTS course_id BIGINT NULL COMMENT 'optional course binding id';

CREATE TABLE IF NOT EXISTS teaching_material_traces (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'trace id',
    material_id BIGINT NOT NULL COMMENT 'material id',
    parse_task_id BIGINT NOT NULL COMMENT 'parse task id',
    course_id BIGINT NULL COMMENT 'course id',
    knowledge_point_id BIGINT NULL COMMENT 'knowledge point id',
    knowledge_point_name VARCHAR(300) NOT NULL COMMENT 'knowledge point name',
    ideology_element VARCHAR(300) NOT NULL COMMENT 'ideology element',
    evidence_snippet VARCHAR(1000) NULL COMMENT 'evidence snippet',
    match_reason VARCHAR(1000) NULL COMMENT 'match reason',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',

    INDEX idx_tmt_material (material_id),
    INDEX idx_tmt_task (parse_task_id),
    INDEX idx_tmt_course (course_id),
    INDEX idx_tmt_knowledge_name (knowledge_point_name),
    INDEX idx_tmt_ideology (ideology_element)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='teaching material traces';
