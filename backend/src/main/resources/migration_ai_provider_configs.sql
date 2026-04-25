CREATE TABLE IF NOT EXISTS ai_provider_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ai provider config id',
    provider_key VARCHAR(50) NOT NULL COMMENT 'provider key',
    label VARCHAR(100) NOT NULL COMMENT 'provider label',
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'enabled flag',
    api_base VARCHAR(500) NULL COMMENT 'api base url',
    model VARCHAR(100) NULL COMMENT 'model name',
    api_key VARCHAR(500) NULL COMMENT 'api key',
    timeout_seconds INT NOT NULL DEFAULT 120 COMMENT 'timeout seconds',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    UNIQUE INDEX uk_ai_provider_key (provider_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ai provider config';

CREATE TABLE IF NOT EXISTS ai_route_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ai route config id',
    task_type VARCHAR(50) NOT NULL COMMENT 'task type',
    provider_key VARCHAR(50) NOT NULL COMMENT 'provider key',
    model VARCHAR(100) NULL COMMENT 'model override',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    UNIQUE INDEX uk_ai_route_task (task_type),
    INDEX idx_ai_route_provider (provider_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ai route config';
