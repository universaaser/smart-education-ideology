-- =====================================================
-- material trace + course binding migration
-- =====================================================

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'parse_tasks'
      AND column_name = 'course_id'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE parse_tasks ADD COLUMN course_id BIGINT NULL COMMENT ''optional course binding id''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'teaching_materials'
      AND column_name = 'course_id'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE teaching_materials ADD COLUMN course_id BIGINT NULL COMMENT ''optional course binding id''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
    resource_title VARCHAR(300) NULL COMMENT 'resource title',
    resource_source VARCHAR(200) NULL COMMENT 'resource source',
    resource_source_url VARCHAR(500) NULL COMMENT 'resource source url',
    resource_quoted_excerpt TEXT NULL COMMENT 'resource quoted excerpt',
    citation_explanation TEXT NULL COMMENT 'citation explanation',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',

    INDEX idx_tmt_material (material_id),
    INDEX idx_tmt_task (parse_task_id),
    INDEX idx_tmt_course (course_id),
    INDEX idx_tmt_knowledge_name (knowledge_point_name),
    INDEX idx_tmt_ideology (ideology_element)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='teaching material traces';

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'teaching_material_traces'
      AND column_name = 'resource_title'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE teaching_material_traces ADD COLUMN resource_title VARCHAR(300) NULL COMMENT ''resource title'' AFTER match_reason',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'teaching_material_traces'
      AND column_name = 'resource_source'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE teaching_material_traces ADD COLUMN resource_source VARCHAR(200) NULL COMMENT ''resource source'' AFTER resource_title',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'teaching_material_traces'
      AND column_name = 'resource_source_url'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE teaching_material_traces ADD COLUMN resource_source_url VARCHAR(500) NULL COMMENT ''resource source url'' AFTER resource_source',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'teaching_material_traces'
      AND column_name = 'resource_quoted_excerpt'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE teaching_material_traces ADD COLUMN resource_quoted_excerpt TEXT NULL COMMENT ''resource quoted excerpt'' AFTER resource_source_url',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'teaching_material_traces'
      AND column_name = 'citation_explanation'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE teaching_material_traces ADD COLUMN citation_explanation TEXT NULL COMMENT ''citation explanation'' AFTER resource_quoted_excerpt',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
