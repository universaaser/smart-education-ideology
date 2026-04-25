-- =====================================================
-- Phase 2: projection tables + FULLTEXT migration
-- Purpose: flatten LLM pipeline results into queryable relational tables
-- =====================================================

-- ---------- parse_task_knowledge_points ----------
CREATE TABLE IF NOT EXISTS parse_task_knowledge_points (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'projection row id',
    parse_task_id BIGINT NOT NULL COMMENT 'related parse task id',
    course_id BIGINT NULL COMMENT 'optional course binding id',
    point_name VARCHAR(200) NOT NULL COMMENT 'knowledge point name',
    definition TEXT COMMENT 'point definition',
    chapter VARCHAR(200) COMMENT 'belonging chapter',
    evidence_snippet TEXT COMMENT 'evidence snippet from document',
    resource_citations_json TEXT COMMENT 'resource citations json',
    pipeline_version VARCHAR(20) DEFAULT 'v1' COMMENT 'pipeline schema version',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',

    INDEX idx_ptkp_task (parse_task_id),
    INDEX idx_ptkp_course_point (course_id, point_name),
    FULLTEXT INDEX ft_ptkp_def (point_name, definition, evidence_snippet)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='parse task knowledge points projection';

-- ---------- parse_task_ideology_matches ----------
CREATE TABLE IF NOT EXISTS parse_task_ideology_matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'projection row id',
    parse_task_id BIGINT NOT NULL COMMENT 'related parse task id',
    knowledge_point_name VARCHAR(200) NOT NULL COMMENT 'knowledge point name',
    ideology_element VARCHAR(120) NOT NULL COMMENT 'matched ideology element',
    match_reason TEXT COMMENT 'match reason',
    citation_explanation TEXT COMMENT 'citation explanation',
    resource_citations_json TEXT COMMENT 'resource citations json',
    pipeline_version VARCHAR(20) DEFAULT 'v1' COMMENT 'pipeline schema version',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',

    INDEX idx_ptim_task (parse_task_id),
    INDEX idx_ptim_ideology (ideology_element),
    FULLTEXT INDEX ft_ptim_reason (match_reason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='parse task ideology matches projection';

-- ---------- teaching_materials FULLTEXT ----------
ALTER TABLE teaching_materials ADD FULLTEXT INDEX ft_lecture_notes (lecture_notes);
