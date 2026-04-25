CREATE TABLE IF NOT EXISTS course_chapters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'course chapter id',
    course_id BIGINT NOT NULL COMMENT 'course id',
    parent_id BIGINT NULL COMMENT 'parent chapter id',
    title VARCHAR(200) NOT NULL COMMENT 'chapter title',
    sort_order INT DEFAULT 0 COMMENT 'display order',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    INDEX idx_course_chapter_course (course_id, sort_order),
    INDEX idx_course_chapter_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='course chapters';

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'teaching_materials'
      AND column_name = 'chapter_id'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE teaching_materials ADD COLUMN chapter_id BIGINT NULL COMMENT ''optional course chapter id''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'teaching_materials'
      AND index_name = 'idx_tm_chapter'
);
SET @sql := IF(
    @index_exists = 0,
    'ALTER TABLE teaching_materials ADD INDEX idx_tm_chapter (chapter_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
