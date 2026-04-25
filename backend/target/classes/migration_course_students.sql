CREATE TABLE IF NOT EXISTS course_students (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'course student binding id',
    course_id BIGINT NOT NULL COMMENT 'course id',
    student_id BIGINT NOT NULL COMMENT 'student user id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',

    UNIQUE INDEX uk_course_student (course_id, student_id),
    INDEX idx_course_student_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='course student binding';
