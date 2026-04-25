CREATE TABLE IF NOT EXISTS crawl_sources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'crawl source id',
    name VARCHAR(120) NOT NULL COMMENT 'source name',
    base_url VARCHAR(500) NOT NULL COMMENT 'base or list url',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'enabled flag',
    remark VARCHAR(500) NULL COMMENT 'remark',
    last_run_at DATETIME NULL COMMENT 'last run time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    UNIQUE INDEX uk_crawl_source_base_url (base_url),
    INDEX idx_crawl_source_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='crawl sources';

CREATE TABLE IF NOT EXISTS crawl_run_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'crawl run log id',
    source_id BIGINT NULL COMMENT 'source id',
    source_name VARCHAR(120) NULL COMMENT 'source name snapshot',
    status VARCHAR(30) NOT NULL COMMENT 'run status',
    total_fetched INT DEFAULT 0 COMMENT 'fetched count',
    total_created INT DEFAULT 0 COMMENT 'created count',
    total_deduplicated INT DEFAULT 0 COMMENT 'deduplicated count',
    total_failed INT DEFAULT 0 COMMENT 'failed count',
    error_summary VARCHAR(500) NULL COMMENT 'error summary',
    stats_json JSON NULL COMMENT 'site stats snapshot',
    started_at DATETIME NULL COMMENT 'started time',
    finished_at DATETIME NULL COMMENT 'finished time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',

    INDEX idx_crawl_run_source (source_id, started_at),
    INDEX idx_crawl_run_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='crawl run logs';

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'resources'
      AND column_name = 'review_status'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE resources ADD COLUMN review_status VARCHAR(30) NOT NULL DEFAULT ''APPROVED'' COMMENT ''review status''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'resources'
      AND column_name = 'reviewed_by'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE resources ADD COLUMN reviewed_by BIGINT NULL COMMENT ''reviewer id''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'resources'
      AND column_name = 'reviewed_at'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE resources ADD COLUMN reviewed_at DATETIME NULL COMMENT ''review time''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'resources'
      AND index_name = 'idx_resource_review_status'
);
SET @sql := IF(
    @index_exists = 0,
    'ALTER TABLE resources ADD INDEX idx_resource_review_status (review_status)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
