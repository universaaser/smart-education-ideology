-- Safe migration script for splitting legacy knowledge_points into
-- subject_knowledge and ideology_knowledge runtime tables.
-- This script avoids DROP statements so it can be executed on an existing database.

USE smart_education;

CREATE TABLE IF NOT EXISTS ideology_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    name VARCHAR(100) NOT NULL COMMENT 'Fixed ideology category name',
    description TEXT COMMENT 'Category description',
    keywords VARCHAR(500) COMMENT 'Category keywords',
    sort_order INT DEFAULT 0 COMMENT 'Display order',
    position_x DOUBLE DEFAULT 0 COMMENT 'Graph position x',
    position_y DOUBLE DEFAULT 0 COMMENT 'Graph position y',
    node_size VARCHAR(20) DEFAULT 'MD' COMMENT 'Graph node size',
    icon VARCHAR(100) COMMENT 'Icon identifier',
    tag VARCHAR(100) COMMENT 'Tag field',
    sub_title VARCHAR(255) COMMENT 'Secondary title',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',
    UNIQUE KEY uk_ideology_knowledge_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fixed ideology knowledge dictionary';

CREATE TABLE IF NOT EXISTS subject_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    name VARCHAR(200) NOT NULL COMMENT 'Subject knowledge name',
    subject VARCHAR(100) COMMENT 'Subject field',
    category VARCHAR(100) COMMENT 'Category field',
    summary TEXT COMMENT 'Condensed subject summary',
    ideology_summary TEXT COMMENT 'Condensed ideology summary',
    source_url VARCHAR(500) COMMENT 'Representative source url',
    position_x DOUBLE DEFAULT 0 COMMENT 'Graph position x',
    position_y DOUBLE DEFAULT 0 COMMENT 'Graph position y',
    node_size VARCHAR(20) DEFAULT 'MD' COMMENT 'Graph node size',
    icon VARCHAR(100) COMMENT 'Icon identifier',
    tag VARCHAR(100) COMMENT 'Tag field',
    sub_title VARCHAR(255) COMMENT 'Secondary title',
    creator_id BIGINT COMMENT 'Creator id',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',
    INDEX idx_subject_knowledge_name (name),
    INDEX idx_subject_knowledge_subject (subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Runtime subject knowledge table';

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ideology_knowledge'
      AND column_name = 'deleted'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE ideology_knowledge ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''Soft delete flag''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'subject_knowledge'
      AND column_name = 'deleted'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE subject_knowledge ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''Soft delete flag''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS subject_ideology_matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    subject_knowledge_id BIGINT NOT NULL COMMENT 'Subject knowledge id',
    ideology_knowledge_id BIGINT NOT NULL COMMENT 'Ideology knowledge id',
    is_primary TINYINT DEFAULT 0 COMMENT 'Whether this is the primary ideology match',
    match_score DECIMAL(5,2) DEFAULT 0 COMMENT 'Matching score',
    match_reason TEXT COMMENT 'Matching reason',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    UNIQUE KEY uk_subject_ideology_match (subject_knowledge_id, ideology_knowledge_id),
    INDEX idx_subject_ideology_subject (subject_knowledge_id),
    INDEX idx_subject_ideology_ideology (ideology_knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Subject to ideology match table';

CREATE TABLE IF NOT EXISTS course_subject_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    course_id BIGINT NOT NULL COMMENT 'Course id',
    subject_knowledge_id BIGINT NOT NULL COMMENT 'Subject knowledge id',
    sort_order INT DEFAULT 0 COMMENT 'Display order',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    UNIQUE KEY uk_course_subject_knowledge (course_id, subject_knowledge_id),
    INDEX idx_course_subject_course (course_id),
    INDEX idx_course_subject_knowledge (subject_knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Course to subject knowledge match table';

CREATE TABLE IF NOT EXISTS subject_knowledge_sources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    subject_knowledge_id BIGINT NOT NULL COMMENT 'Subject knowledge id',
    resource_id BIGINT NOT NULL COMMENT 'Resource id',
    source_type VARCHAR(50) COMMENT 'Source type',
    source_url VARCHAR(500) COMMENT 'Source url',
    excerpt TEXT COMMENT 'Condensed excerpt',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    UNIQUE KEY uk_subject_resource (subject_knowledge_id, resource_id),
    INDEX idx_subject_source_subject (subject_knowledge_id),
    INDEX idx_subject_source_resource (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Subject knowledge source trace table';

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '工匠精神', 'Focus on precision, patience, and continuous improvement in professional work.', 'quality,detail,craftsmanship,improvement', 1, -420, -180, 'LG', 'hammer', 'ideology', 'Professional Excellence'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '工匠精神');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '产业创新', 'Emphasize industrial upgrading, problem solving, and innovation-driven development.', 'industry,innovation,development,upgrade', 2, -260, -300, 'LG', 'factory', 'ideology', 'Innovation Drive'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '产业创新');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '科技报国', 'Highlight serving national development through science and technology.', 'technology,nation,service,mission', 3, -80, -220, 'LG', 'flag', 'ideology', 'National Service'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '科技报国');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '家国情怀', 'Promote collective responsibility and commitment to the country and society.', 'country,society,responsibility,commitment', 4, 80, -320, 'LG', 'home', 'ideology', 'Collective Commitment'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '家国情怀');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '责任担当', 'Encourage taking responsibility, facing challenges, and delivering outcomes.', 'responsibility,accountability,action,challenge', 5, 260, -200, 'LG', 'shield', 'ideology', 'Action Responsibility'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '责任担当');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '绿色发展', 'Stress sustainability, efficiency, and ecological awareness in development.', 'green,sustainable,efficiency,ecology', 6, 420, -320, 'LG', 'leaf', 'ideology', 'Sustainable Growth'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '绿色发展');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '依法治理', 'Promote rule-based governance, standards, and procedural discipline.', 'rule,governance,standard,discipline', 7, -360, 80, 'LG', 'balance-scale', 'ideology', 'Rule Orientation'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '依法治理');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '职业操守', 'Strengthen ethics, compliance, and professional conduct.', 'ethics,compliance,conduct,profession', 8, -120, 220, 'LG', 'badge-check', 'ideology', 'Professional Ethics'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '职业操守');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '协同共治', 'Emphasize collaboration, coordination, and shared governance.', 'collaboration,coordination,co-governance,teamwork', 9, 120, 100, 'LG', 'users', 'ideology', 'Shared Governance'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '协同共治');

INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title)
SELECT '文化自信', 'Reflect confidence in local values, narratives, and cultural identity.', 'culture,confidence,identity,value', 10, 360, 220, 'LG', 'book-open', 'ideology', 'Cultural Identity'
WHERE NOT EXISTS (SELECT 1 FROM ideology_knowledge WHERE name = '文化自信');

INSERT INTO subject_knowledge (
    name, subject, category, summary, ideology_summary, source_url,
    position_x, position_y, node_size, icon, tag, sub_title, creator_id, created_at, updated_at
)
SELECT
    kp.name,
    kp.subject,
    kp.category,
    kp.technical_definition,
    kp.ideological_value,
    kp.source_url,
    COALESCE(kp.position_x, 0),
    COALESCE(kp.position_y, 0),
    COALESCE(kp.node_size, 'MD'),
    kp.icon,
    kp.tag,
    kp.sub_title,
    kp.creator_id,
    COALESCE(kp.created_at, NOW()),
    COALESCE(kp.updated_at, NOW())
FROM knowledge_points kp
LEFT JOIN subject_knowledge sk
    ON sk.name = kp.name AND COALESCE(sk.subject, '') = COALESCE(kp.subject, '')
WHERE kp.node_type = 'TECH'
  AND sk.id IS NULL;

INSERT INTO course_subject_knowledge (course_id, subject_knowledge_id, sort_order, created_at, updated_at)
SELECT
    ckp.course_id,
    sk.id,
    COALESCE(ckp.sort_order, 0),
    COALESCE(ckp.created_at, NOW()),
    COALESCE(ckp.updated_at, NOW())
FROM course_knowledge_points ckp
JOIN knowledge_points kp ON kp.id = ckp.knowledge_point_id AND kp.node_type = 'TECH'
JOIN subject_knowledge sk ON sk.name = kp.name AND COALESCE(sk.subject, '') = COALESCE(kp.subject, '')
LEFT JOIN course_subject_knowledge csk
    ON csk.course_id = ckp.course_id AND csk.subject_knowledge_id = sk.id
WHERE csk.id IS NULL;

INSERT INTO subject_knowledge_sources (
    subject_knowledge_id, resource_id, source_type, source_url, excerpt, created_at, updated_at
)
SELECT
    sk.id,
    kps.resource_id,
    kps.source_type,
    kps.source_url,
    kps.excerpt,
    COALESCE(kps.created_at, NOW()),
    COALESCE(kps.updated_at, NOW())
FROM knowledge_point_sources kps
JOIN knowledge_points kp ON kp.id = kps.knowledge_point_id AND kp.node_type = 'TECH'
JOIN subject_knowledge sk ON sk.name = kp.name AND COALESCE(sk.subject, '') = COALESCE(kp.subject, '')
LEFT JOIN subject_knowledge_sources sks
    ON sks.subject_knowledge_id = sk.id AND sks.resource_id = kps.resource_id
WHERE sks.id IS NULL;

INSERT INTO subject_ideology_matches (
    subject_knowledge_id, ideology_knowledge_id, is_primary, match_score, match_reason, created_at, updated_at
)
SELECT
    sk.id,
    ik.id,
    1,
    95.00,
    COALESCE(kp.ideological_value, 'Migrated from legacy knowledge_points ideology summary.'),
    NOW(),
    NOW()
FROM knowledge_points kp
JOIN subject_knowledge sk ON sk.name = kp.name AND COALESCE(sk.subject, '') = COALESCE(kp.subject, '')
JOIN ideology_knowledge ik ON ik.name = '工匠精神'
LEFT JOIN subject_ideology_matches sim
    ON sim.subject_knowledge_id = sk.id AND sim.ideology_knowledge_id = ik.id
WHERE kp.node_type = 'TECH'
  AND kp.ideological_value IS NOT NULL
  AND kp.ideological_value <> ''
  AND sim.id IS NULL;
