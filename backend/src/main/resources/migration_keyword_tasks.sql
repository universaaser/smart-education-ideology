CREATE TABLE IF NOT EXISTS keyword_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'keyword task id',
    course_id BIGINT NOT NULL COMMENT 'course id',
    creator_id BIGINT NULL COMMENT 'creator user id',
    keywords VARCHAR(1000) NOT NULL COMMENT 'comma separated keywords',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'task status',
    result_summary VARCHAR(500) NULL COMMENT 'result summary',
    error_summary VARCHAR(500) NULL COMMENT 'error summary',
    finished_at DATETIME NULL COMMENT 'finished time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    INDEX idx_keyword_task_course (course_id, created_at),
    INDEX idx_keyword_task_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='keyword collection tasks';

CREATE TABLE IF NOT EXISTS keyword_task_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'keyword task item id',
    task_id BIGINT NOT NULL COMMENT 'keyword task id',
    keyword VARCHAR(80) NOT NULL COMMENT 'keyword',
    title VARCHAR(200) NOT NULL COMMENT 'result title',
    source_url VARCHAR(500) NULL COMMENT 'source url',
    excerpt TEXT NULL COMMENT 'source excerpt',
    ai_summary TEXT NULL COMMENT 'AI summary',
    ideology_tags VARCHAR(500) NULL COMMENT 'ideology tags json',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'item status',
    resource_id BIGINT NULL COMMENT 'accepted resource id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    INDEX idx_keyword_item_task (task_id, id),
    INDEX idx_keyword_item_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='keyword collection task items';
