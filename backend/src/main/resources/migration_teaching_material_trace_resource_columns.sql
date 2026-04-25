-- =====================================================
-- teaching material trace resource citation columns repair
-- =====================================================

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
