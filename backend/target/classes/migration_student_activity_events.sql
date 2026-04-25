CREATE TABLE IF NOT EXISTS student_activity_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'event id',
    student_id BIGINT NOT NULL COMMENT 'student user id',
    course_id BIGINT NOT NULL COMMENT 'course id',
    event_type VARCHAR(50) NOT NULL COMMENT 'page_stay/material_open/knowledge_view/ai_ask/answer_submit/path_switch',
    knowledge_point_id BIGINT COMMENT 'knowledge point id',
    duration_seconds INT DEFAULT 0 COMMENT 'duration seconds',
    payload_json JSON COMMENT 'event payload',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'event time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',

    INDEX idx_sae_student_course_time (student_id, course_id, occurred_at),
    INDEX idx_sae_event_type (event_type),
    INDEX idx_sae_knowledge_point (knowledge_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='student activity event stream';
