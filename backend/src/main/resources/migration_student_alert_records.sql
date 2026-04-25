CREATE TABLE IF NOT EXISTS student_alert_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'alert record id',
    student_id BIGINT NOT NULL COMMENT 'student user id',
    course_id BIGINT NOT NULL COMMENT 'course id',
    alert_type VARCHAR(50) NOT NULL COMMENT 'LOW_ACTIVITY/LOW_STUDY_TIME/CONFUSION_RISK/LOW_RESOURCE_ENGAGEMENT',
    alert_level TINYINT NOT NULL DEFAULT 1 COMMENT '1 mild 2 moderate 3 severe',
    title VARCHAR(200) NOT NULL COMMENT 'alert title',
    message VARCHAR(500) NOT NULL COMMENT 'teacher-facing alert message',
    suggestion VARCHAR(500) COMMENT 'student-facing suggestion',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/IGNORED',
    evidence_json JSON COMMENT 'alert evidence snapshot',
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'generated time',
    handled_at DATETIME COMMENT 'handled time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    INDEX idx_sar_course_status_level (course_id, status, alert_level),
    INDEX idx_sar_student_course_status (student_id, course_id, status),
    INDEX idx_sar_generated_at (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='student alert records';
