ALTER TABLE subject_ideology_matches
    ADD COLUMN review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'review status' AFTER match_reason,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT 'review version' AFTER review_status,
    ADD COLUMN reviewer_id BIGINT NULL COMMENT 'reviewer user id' AFTER version,
    ADD COLUMN reviewed_at DATETIME NULL COMMENT 'reviewed time' AFTER reviewer_id,
    ADD COLUMN review_comment VARCHAR(500) NULL COMMENT 'review comment' AFTER reviewed_at,
    ADD INDEX idx_sim_review_status (review_status, created_at);

CREATE TABLE IF NOT EXISTS subject_ideology_match_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'match review id',
    match_id BIGINT NOT NULL COMMENT 'subject ideology match id',
    action VARCHAR(30) NOT NULL COMMENT 'review action',
    previous_status VARCHAR(30) NULL COMMENT 'previous status',
    next_status VARCHAR(30) NULL COMMENT 'next status',
    previous_reason TEXT NULL COMMENT 'previous match reason',
    next_reason TEXT NULL COMMENT 'next match reason',
    reviewer_id BIGINT NULL COMMENT 'reviewer user id',
    review_comment VARCHAR(500) NULL COMMENT 'review comment',
    version INT NOT NULL DEFAULT 1 COMMENT 'version after action',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',

    INDEX idx_match_review_match (match_id, created_at),
    INDEX idx_match_review_action (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='subject ideology match review history';
