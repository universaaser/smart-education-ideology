-- =====================================================
-- 智教思政系统数据库初始化脚本
-- 数据库名：smart_education
-- 字符集：utf8mb4
-- =====================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS smart_education 
    DEFAULT CHARACTER SET utf8mb4 
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_education;

-- =====================================================
-- 1. 用户表 (users)
-- 说明：存储教师和学生用户信息
-- =====================================================
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名/教工号',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    email VARCHAR(100) COMMENT '邮箱地址',
    real_name VARCHAR(50) COMMENT '真实姓名',
    role ENUM('TEACHER', 'STUDENT', 'ADMIN') NOT NULL DEFAULT 'TEACHER' COMMENT '角色类型',
    avatar VARCHAR(255) COMMENT '头像URL',
    department VARCHAR(100) COMMENT '院系/专业',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    last_login_time DATETIME COMMENT '最后登录时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =====================================================
-- 2. 知识点表 (knowledge_points)
-- 说明：存储专业知识点及其思政价值映射
-- =====================================================
DROP TABLE IF EXISTS knowledge_points;
CREATE TABLE knowledge_points (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识点ID',
    name VARCHAR(200) NOT NULL COMMENT '专业术语名称',
    subject VARCHAR(100) NOT NULL COMMENT '所属学科',
    category VARCHAR(100) COMMENT '分类（如：物联网、计算机科学等）',
    technical_definition TEXT COMMENT '技术定义',
    ideological_value TEXT COMMENT '思政价值（AI挖掘结果）',
    source_url VARCHAR(500) COMMENT '来源链接',
    document_id BIGINT COMMENT '关联文档ID',
    node_type ENUM('TECH', 'IDEO') NOT NULL DEFAULT 'TECH' COMMENT '节点类型：TECH-技术节点 IDEO-思政节点',
    position_x DOUBLE DEFAULT 0 COMMENT '图谱中X坐标',
    position_y DOUBLE DEFAULT 0 COMMENT '图谱中Y坐标',
    node_size ENUM('LG', 'MD', 'SM') DEFAULT 'MD' COMMENT '节点大小',
    icon VARCHAR(50) COMMENT '节点图标',
    tag VARCHAR(100) COMMENT '节点标签',
    sub_title VARCHAR(200) COMMENT '副标题',
    creator_id BIGINT COMMENT '创建者ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    
    INDEX idx_subject (subject),
    INDEX idx_category (category),
    INDEX idx_node_type (node_type),
    INDEX idx_creator (creator_id),
    FULLTEXT INDEX ft_name_definition (name, technical_definition)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';

-- =====================================================
-- 3. 知识关系表 (knowledge_relations)
-- 说明：用于构建知识图谱的边关系
-- =====================================================
DROP TABLE IF EXISTS knowledge_relations;
CREATE TABLE knowledge_relations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系ID',
    from_node_id BIGINT NOT NULL COMMENT '起始节点ID',
    to_node_id BIGINT NOT NULL COMMENT '结束节点ID',
    relation_type VARCHAR(50) NOT NULL COMMENT '关系类型：TECH_BASE、VALUE_SHOW、THEORY_SUPPORT、PRACTICE_APPLY',
    line_style ENUM('SOLID', 'DASHED') DEFAULT 'SOLID' COMMENT '连线样式',
    weight DOUBLE DEFAULT 1.0 COMMENT '关系权重',
    description VARCHAR(500) COMMENT '关系描述',
    creator_id BIGINT COMMENT '创建者ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    
    INDEX idx_from_node (from_node_id),
    INDEX idx_to_node (to_node_id),
    INDEX idx_relation_type (relation_type),
    UNIQUE INDEX uk_relation (from_node_id, to_node_id, relation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识关系表';

-- =====================================================
-- 3.1 思政知识表 (ideology_knowledge)
-- 说明：固定思政种类字典表，仅存四字浓缩思政元素
-- =====================================================
DROP TABLE IF EXISTS ideology_knowledge;
CREATE TABLE ideology_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '思政知识ID',
    name VARCHAR(100) NOT NULL COMMENT '思政元素名称（四字浓缩）',
    description TEXT COMMENT '思政元素说明',
    keywords VARCHAR(500) COMMENT '匹配关键词，使用逗号分隔',
    sort_order INT DEFAULT 0 COMMENT '排序值',
    position_x DOUBLE DEFAULT 0 COMMENT '图谱中X坐标',
    position_y DOUBLE DEFAULT 0 COMMENT '图谱中Y坐标',
    node_size ENUM('LG', 'MD', 'SM') DEFAULT 'MD' COMMENT '节点大小',
    icon VARCHAR(50) COMMENT '节点图标',
    tag VARCHAR(100) COMMENT '节点标签',
    sub_title VARCHAR(200) COMMENT '副标题',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    UNIQUE INDEX uk_ideology_name (name),
    INDEX idx_ideology_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='思政知识表';

-- =====================================================
-- 3.2 学科知识表 (subject_knowledge)
-- 说明：运行时主表，存储学科知识及其浓缩摘要
-- =====================================================
DROP TABLE IF EXISTS subject_knowledge;
CREATE TABLE subject_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学科知识ID',
    name VARCHAR(200) NOT NULL COMMENT '学科知识名称',
    subject VARCHAR(100) NOT NULL COMMENT '所属学科',
    category VARCHAR(100) COMMENT '分类',
    summary TEXT COMMENT '学科知识浓缩摘要',
    ideology_summary TEXT COMMENT '思政匹配理由摘要',
    source_url VARCHAR(500) COMMENT '来源链接',
    position_x DOUBLE DEFAULT 0 COMMENT '图谱中X坐标',
    position_y DOUBLE DEFAULT 0 COMMENT '图谱中Y坐标',
    node_size ENUM('LG', 'MD', 'SM') DEFAULT 'MD' COMMENT '节点大小',
    icon VARCHAR(50) COMMENT '节点图标',
    tag VARCHAR(100) COMMENT '节点标签',
    sub_title VARCHAR(200) COMMENT '副标题',
    creator_id BIGINT COMMENT '创建者ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    INDEX idx_subject_name (name),
    INDEX idx_subject_subject (subject),
    INDEX idx_subject_category (category),
    INDEX idx_subject_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学科知识表';

-- =====================================================
-- 3.3 学科知识-思政知识匹配表 (subject_ideology_matches)
-- 说明：支持一个学科知识映射多个思政种类，并记录主匹配和匹配理由
-- =====================================================
DROP TABLE IF EXISTS subject_ideology_matches;
CREATE TABLE subject_ideology_matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '匹配ID',
    subject_knowledge_id BIGINT NOT NULL COMMENT '学科知识ID',
    ideology_knowledge_id BIGINT NOT NULL COMMENT '思政知识ID',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主匹配：0-否 1-是',
    match_score DECIMAL(5,2) DEFAULT 0 COMMENT '匹配分值',
    match_reason TEXT COMMENT '匹配理由',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_sim_subject_id (subject_knowledge_id),
    INDEX idx_sim_ideology_id (ideology_knowledge_id),
    UNIQUE INDEX uk_subject_ideology (subject_knowledge_id, ideology_knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学科知识思政匹配表';

-- =====================================================
-- 3.4 课程-学科知识关联表 (course_subject_knowledge)
-- 说明：课程只关联学科知识，不直接关联思政知识
-- =====================================================
DROP TABLE IF EXISTS course_subject_knowledge;
CREATE TABLE course_subject_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    subject_knowledge_id BIGINT NOT NULL COMMENT '学科知识ID',
    sort_order INT DEFAULT 0 COMMENT '展示顺序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_csk_course_id (course_id),
    INDEX idx_csk_subject_knowledge_id (subject_knowledge_id),
    UNIQUE INDEX uk_course_subject_knowledge (course_id, subject_knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程学科知识关联表';

-- =====================================================
-- 3.5 学科知识来源追溯表 (subject_knowledge_sources)
-- 说明：只追溯学科知识来源，思政知识本身是固定字典
-- =====================================================
DROP TABLE IF EXISTS subject_knowledge_sources;
CREATE TABLE subject_knowledge_sources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '来源ID',
    subject_knowledge_id BIGINT NOT NULL COMMENT '学科知识ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    source_type VARCHAR(50) NOT NULL COMMENT '来源类型：CRAWLED_RESOURCE/UPLOADED_DOCUMENT',
    source_url VARCHAR(500) COMMENT '来源链接',
    excerpt VARCHAR(500) COMMENT '来源摘录',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_sks_subject_id (subject_knowledge_id),
    INDEX idx_sks_resource_id (resource_id),
    UNIQUE INDEX uk_subject_resource (subject_knowledge_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学科知识来源追溯表';

-- =====================================================
-- 3.6 旧课程-知识点关联表 (course_knowledge_points)
-- 说明：仅保留为迁移源，不再作为运行时主表
-- =====================================================
DROP TABLE IF EXISTS course_knowledge_points;
CREATE TABLE course_knowledge_points (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '旧关联ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    knowledge_point_id BIGINT NOT NULL COMMENT '旧知识点ID',
    sort_order INT DEFAULT 0 COMMENT '展示顺序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_legacy_course_id (course_id),
    INDEX idx_legacy_knowledge_point_id (knowledge_point_id),
    UNIQUE INDEX uk_legacy_course_knowledge (course_id, knowledge_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧课程知识点关联表';

-- =====================================================
-- 3.7 旧知识点来源追溯表 (knowledge_point_sources)
-- 说明：仅保留为迁移源，不再作为运行时主表
-- =====================================================
DROP TABLE IF EXISTS knowledge_point_sources;
CREATE TABLE knowledge_point_sources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '旧来源ID',
    knowledge_point_id BIGINT NOT NULL COMMENT '旧知识点ID',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    source_type VARCHAR(50) NOT NULL COMMENT '来源类型',
    source_url VARCHAR(500) COMMENT '来源链接',
    excerpt VARCHAR(500) COMMENT '来源摘录',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_legacy_kps_knowledge_id (knowledge_point_id),
    INDEX idx_legacy_kps_resource_id (resource_id),
    UNIQUE INDEX uk_legacy_knowledge_resource (knowledge_point_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧知识点来源追溯表';

-- =====================================================
-- 4. 学习行为表 (student_activities)
-- 说明：记录学生学习行为数据，用于预警分析
-- =====================================================
DROP TABLE IF EXISTS student_activities;
CREATE TABLE student_activities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    student_id BIGINT NOT NULL COMMENT '学生用户ID',
    course_id BIGINT COMMENT '课程ID',
    knowledge_point_id BIGINT COMMENT '知识点ID',
    study_duration INT DEFAULT 0 COMMENT '学习时长（分钟）',
    correct_rate DECIMAL(5,2) DEFAULT 0 COMMENT '答题正确率（百分比）',
    focus_score DECIMAL(5,2) DEFAULT 0 COMMENT '专注度评分（0-100）',
    emotion_status VARCHAR(50) COMMENT '情绪状态：NORMAL/CONFUSED/ANGRY/DISTRACTED',
    alert_level TINYINT DEFAULT 0 COMMENT '预警等级：0-正常 1-轻度 2-中度 3-重度',
    alert_message VARCHAR(500) COMMENT '预警消息',
    session_start DATETIME COMMENT '学习会话开始时间',
    session_end DATETIME COMMENT '学习会话结束时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_student (student_id),
    INDEX idx_course (course_id),
    INDEX idx_alert_level (alert_level),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习行为表';

-- =====================================================
-- 5. 课程表 (courses)
-- 说明：存储课程基本信息
-- =====================================================
DROP TABLE IF EXISTS courses;
CREATE TABLE courses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '课程ID',
    name VARCHAR(200) NOT NULL COMMENT '课程名称',
    code VARCHAR(50) COMMENT '课程编码',
    description TEXT COMMENT '课程描述',
    teacher_id BIGINT NOT NULL COMMENT '授课教师ID',
    semester VARCHAR(50) COMMENT '学期（如：2024春）',
    progress INT DEFAULT 0 COMMENT '课程进度（百分比）',
    ideology_score ENUM('EXCELLENT', 'GOOD', 'FAIR', 'POOR') DEFAULT 'FAIR' COMMENT '思政融合度评级',
    cover_image VARCHAR(255) COMMENT '课程封面图',
    status TINYINT DEFAULT 1 COMMENT '状态：0-草稿 1-发布 2-归档',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    
    INDEX idx_teacher (teacher_id),
    INDEX idx_semester (semester),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- =====================================================
-- 6. 资源表 (resources)
-- 说明：存储教学资源及其思政分析结果
-- =====================================================
DROP TABLE IF EXISTS resources;
CREATE TABLE resources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '资源ID',
    title VARCHAR(300) NOT NULL COMMENT '资源标题',
    source VARCHAR(200) COMMENT '来源出处',
    source_url VARCHAR(500) COMMENT '来源链接（用于去重与追溯）',
    category VARCHAR(100) COMMENT '学科分类',
    content TEXT COMMENT '知识点原文内容',
    ideology_summary TEXT COMMENT 'LLM提取的思政价值总结',
    tags VARCHAR(500) COMMENT '思政标签（JSON数组）',
    file_path VARCHAR(500) COMMENT '文件存储路径',
    file_type VARCHAR(20) COMMENT '文件类型',
    file_size BIGINT COMMENT '文件大小（字节）',
    sync_status ENUM('PENDING', 'PROCESSING', 'SYNCED', 'FAILED') DEFAULT 'PENDING' COMMENT '同步状态',
    parse_task_id BIGINT COMMENT '关联的解析任务ID',
    creator_id BIGINT COMMENT '创建者ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    
    INDEX idx_category (category),
    UNIQUE INDEX uk_source_url (source_url),
    INDEX idx_sync_status (sync_status),
    INDEX idx_creator (creator_id),
    FULLTEXT INDEX ft_search (title, content, ideology_summary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源表';

-- =====================================================
-- 7. AI对话会话表 (chat_sessions)
-- 说明：存储AI助手对话会话
-- =====================================================
DROP TABLE IF EXISTS chat_sessions;
CREATE TABLE chat_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(200) COMMENT '会话标题',
    summary VARCHAR(500) COMMENT '会话摘要',
    ai_model VARCHAR(50) COMMENT '使用的AI模型',
    message_count INT DEFAULT 0 COMMENT '消息数量',
    last_message_at DATETIME COMMENT '最后消息时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    
    INDEX idx_user (user_id),
    INDEX idx_last_message (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话会话表';

-- =====================================================
-- 8. AI对话消息表 (chat_messages)
-- 说明：存储AI助手对话消息内容
-- =====================================================
DROP TABLE IF EXISTS chat_messages;
CREATE TABLE chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role ENUM('USER', 'ASSISTANT', 'SYSTEM') NOT NULL COMMENT '角色',
    content TEXT NOT NULL COMMENT '消息内容',
    content_type VARCHAR(20) DEFAULT 'TEXT' COMMENT '内容类型：TEXT/HTML/MARKDOWN',
    tokens_used INT COMMENT '消耗的Token数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_session (session_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话消息表';

-- =====================================================
-- 9. 文档解析任务表 (parse_tasks)
-- 说明：记录文档解析任务状态
-- =====================================================
DROP TABLE IF EXISTS parse_tasks;
CREATE TABLE parse_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    file_name VARCHAR(300) NOT NULL COMMENT '文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '文件路径',
    file_size BIGINT COMMENT '文件大小（字节）',
    status ENUM('PENDING', 'UPLOADING', 'PARSING', 'ANALYZING', 'COMPLETED', 'FAILED') DEFAULT 'PENDING' COMMENT '任务状态',
    progress INT DEFAULT 0 COMMENT '处理进度（百分比）',
    current_step VARCHAR(200) COMMENT '当前处理步骤描述',
    parsed_content TEXT COMMENT 'MinerU解析后的结构化文本',
    ai_analysis TEXT COMMENT 'AI分析结果（思政融合建议）',
    error_message VARCHAR(500) COMMENT '错误信息',
    started_at DATETIME COMMENT '开始处理时间',
    completed_at DATETIME COMMENT '完成时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档解析任务表';

-- =====================================================
-- 10. 系统动态表 (system_activities)
-- 说明：记录系统动态消息
-- =====================================================
DROP TABLE IF EXISTS system_activities;
CREATE TABLE system_activities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '动态ID',
    user_id BIGINT COMMENT '关联用户ID',
    activity_type VARCHAR(50) NOT NULL COMMENT '动态类型：AI_ANALYSIS/UPLOAD/ALERT/GRAPH_UPDATE',
    title VARCHAR(200) NOT NULL COMMENT '动态标题',
    description VARCHAR(500) COMMENT '动态描述',
    icon VARCHAR(50) COMMENT '图标名称',
    reference_id BIGINT COMMENT '关联对象ID',
    reference_type VARCHAR(50) COMMENT '关联对象类型',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_user (user_id),
    INDEX idx_type (activity_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统动态表';

-- =====================================================
-- 初始数据插入
-- =====================================================

-- 插入默认管理员用户（密码：admin123，使用 BCrypt 加密）
INSERT INTO users (username, password, email, real_name, role, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb.6Q5Zx8eqJvKAoKMpgWpKFrXKQPJ3e.t/5XZpMu', 'admin@smartedu.com', '系统管理员', 'ADMIN', 1),
('teacher_li', '$2a$10$N.zmdr9k7uOCQb.6Q5Zx8eqJvKAoKMpgWpKFrXKQPJ3e.t/5XZpMu', 'li@university.edu.cn', '李老师', 'TEACHER', 1);

-- 插入示例知识点数据
INSERT INTO knowledge_points (name, subject, category, technical_definition, ideological_value, node_type, position_x, position_y, node_size, icon, sub_title) VALUES
('CPU设计', '计算机组成原理', '计算机科学', '研究中央处理器的架构设计，包括控制单元、算术逻辑单元以及指令集的实现。', '在CPU设计的精细调试过程中，引导学生体悟"失之毫厘谬以千里"的严谨态度，通过底层架构国产化进程强化使命感。', 'TECH', 0, 0, 'LG', 'memory', '计算机组成原理'),
('指令集', '计算机组成原理', '计算机科学', '计算机处理器能够识别和执行的指令集合。', NULL, 'TECH', -180, -120, 'MD', NULL, NULL),
('内存管理', '计算机组成原理', '计算机科学', '操作系统对内存资源进行分配和管理的机制。', NULL, 'TECH', 180, -120, 'MD', NULL, NULL),
('I/O系统', '计算机组成原理', '计算机科学', '计算机与外部设备进行数据交换的系统。', NULL, 'TECH', 300, -220, 'SM', NULL, NULL),
('工匠精神', '思政教育', '思政', '在工程实践中秉持极致优化的态度，是对专业技能的尊重与信仰。', '培养学生精益求精、追求卓越的职业精神，传承中华民族优秀的工匠文化传统。', 'IDEO', 0, 180, 'MD', 'workspace_premium', '核心思政点');

-- 插入示例关系数据
INSERT INTO knowledge_relations (from_node_id, to_node_id, relation_type, line_style) VALUES
(1, 2, 'TECH_BASE', 'SOLID'),
(1, 3, 'TECH_BASE', 'SOLID'),
(3, 4, 'TECH_BASE', 'SOLID'),
(1, 5, 'VALUE_SHOW', 'DASHED');

-- 插入示例资源数据
INSERT INTO resources (title, source, category, content, ideology_summary, tags, sync_status) VALUES
('物联网传感器与国家工业安全', '人民日报', '物联网', '传感器作为物联网系统的"五官"，是实现万物互联的基石。在工业生产中，高精度的工业级传感器不仅能够实时监测设备的运行状态，确保生产安全，更是实现智能制造转型的关键突破口。', '通过对"卡脖子"技术困境的分析，引导学生树立科技报国的远大志向。结合自主研发传感器的艰辛历程，强调独立自主、艰苦奋斗的科研精神。', '["家国情怀", "科技自强"]', 'SYNCED'),
('人工智能大模型中的伦理边界探讨', 'arXiv (AI Ethics)', '计算机科学', '大语言模型在极大提高生产力的同时，也带来了版权、隐私以及虚假信息生成的风险。如何在算法设计之初就融入伦理审查机制，是当前AI研究的核心命题。', '引导学生认识算法背后的价值观引导，培养科技伦理意识，确保技术发展符合人类福祉。', '["职业道德"]', 'PROCESSING'),
('国产自研芯片：从底层架构突破封锁', '科技日报', '电子信息', '从底层物理原理的突破到封装工艺的迭代，每一颗国产芯片的诞生都凝聚了科研人员的艰辛努力。我们必须在核心架构上实现完全自主，才能在数字时代掌握话语权。', '强化科技强国战略。通过国产替代的案例，培养学生的爱国热情与使命感，坚定走中国特色自主创新道路。', '["大国重器", "自立自强"]', 'SYNCED');

-- 插入示例课程数据
INSERT INTO courses (name, code, description, teacher_id, semester, progress, ideology_score) VALUES
('马克思主义基本原理', 'MKS001', '系统学习马克思主义基本原理，培养科学世界观', 2, '2024春', 75, 'EXCELLENT'),
('思想道德与法治', 'SXDD001', '思想道德修养与法律基础课程', 2, '2024春', 40, 'GOOD'),
('中国近现代史纲要', 'ZGJS001', '学习中国近现代历史发展脉络', 2, '2024春', 90, 'EXCELLENT');

-- 插入课程与知识点关联示例数据
INSERT INTO course_knowledge_points (course_id, knowledge_point_id, sort_order) VALUES
(1, 1, 1),
(1, 5, 2),
(2, 5, 1),
(3, 5, 1);

-- 插入知识点来源追溯示例数据
INSERT INTO knowledge_point_sources (knowledge_point_id, resource_id, source_type, source_url, excerpt) VALUES
(1, 3, 'CRAWLED_RESOURCE', 'https://example.com/resource/chip', '国产芯片的自主突破体现了底层架构设计的重要性。'),
(5, 1, 'CRAWLED_RESOURCE', 'https://example.com/resource/sensor', '通过传感器自主研发案例引导学生理解科技报国的价值。');

-- =====================================================
-- 新运行时主表初始化与迁移
-- 说明：knowledge_points 仅保留作旧数据迁移源，不再作为运行时主表
-- =====================================================

-- 插入固定思政知识字典
INSERT INTO ideology_knowledge (name, description, keywords, sort_order, position_x, position_y, node_size, icon, tag, sub_title) VALUES
('工匠精神', '强调严谨细致、精益求精和持续打磨的职业品格。', '严谨,精益求精,质量,细节,打磨', 1, 0, 180, 'MD', 'workspace_premium', '核心种类', '固定思政字典'),
('产业创新', '强调产业升级、自主突破与新质生产力培育。', '创新,升级,产业链,突破,转型', 2, 220, 180, 'MD', 'factory', '发展导向', '固定思政字典'),
('科技报国', '强调关键核心技术攻关和服务国家战略。', '科技强国,自主创新,核心技术,报国,攻关', 3, -220, 180, 'MD', 'rocket_launch', '使命导向', '固定思政字典'),
('家国情怀', '强调将专业发展与国家需求、社会责任相结合。', '国家需求,社会责任,奉献,民族复兴,担当', 4, -440, 180, 'SM', 'flag', '价值导向', '固定思政字典'),
('责任担当', '强调主动履责、直面挑战和守护公共利益。', '责任,担当,守护,使命,尽责', 5, 440, 180, 'SM', 'shield', '价值导向', '固定思政字典'),
('绿色发展', '强调低碳环保、节能减排和可持续发展。', '绿色,低碳,节能,环保,可持续', 6, 0, 320, 'SM', 'eco', '发展导向', '固定思政字典'),
('依法治理', '强调制度意识、规范意识和依法用技。', '法治,规范,制度,治理,合规', 7, 220, 320, 'SM', 'gavel', '治理导向', '固定思政字典'),
('职业操守', '强调诚信、伦理和职业规范。', '伦理,诚信,规范,职业道德,底线', 8, -220, 320, 'SM', 'verified_user', '职业导向', '固定思政字典'),
('协同共治', '强调团队协作、跨界协同和系统治理。', '协作,协同,共治,团队,系统观', 9, 440, 320, 'SM', 'groups', '治理导向', '固定思政字典'),
('文化自信', '强调立足中国实践、坚定道路认同和价值认同。', '中国实践,文化,道路自信,认同,传承', 10, -440, 320, 'SM', 'auto_stories', '文化导向', '固定思政字典');

-- 迁移学科知识到运行时主表
INSERT INTO subject_knowledge (name, subject, category, summary, ideology_summary, source_url, position_x, position_y, node_size, icon, tag, sub_title, creator_id)
SELECT
    name,
    subject,
    category,
    technical_definition,
    ideological_value,
    source_url,
    position_x,
    position_y,
    node_size,
    icon,
    tag,
    sub_title,
    creator_id
FROM knowledge_points
WHERE node_type = 'TECH';

-- 将旧思政知识映射到固定思政字典
UPDATE ideology_knowledge
SET description = (
    SELECT technical_definition
    FROM knowledge_points
    WHERE node_type = 'IDEO' AND name = ideology_knowledge.name
    LIMIT 1
)
WHERE name IN (
    SELECT name
    FROM knowledge_points
    WHERE node_type = 'IDEO'
);

-- 根据旧课程关联迁移到新的课程-学科知识关联表
INSERT INTO course_subject_knowledge (course_id, subject_knowledge_id, sort_order)
SELECT
    ckp.course_id,
    sk.id,
    ckp.sort_order
FROM course_knowledge_points ckp
JOIN knowledge_points kp ON kp.id = ckp.knowledge_point_id
JOIN subject_knowledge sk ON sk.name = kp.name AND sk.subject = kp.subject
WHERE kp.node_type = 'TECH';

-- 根据旧来源追溯迁移到新的学科知识来源追溯表
INSERT INTO subject_knowledge_sources (subject_knowledge_id, resource_id, source_type, source_url, excerpt)
SELECT
    sk.id,
    kps.resource_id,
    kps.source_type,
    kps.source_url,
    kps.excerpt
FROM knowledge_point_sources kps
JOIN knowledge_points kp ON kp.id = kps.knowledge_point_id
JOIN subject_knowledge sk ON sk.name = kp.name AND sk.subject = kp.subject
WHERE kp.node_type = 'TECH';

-- 基于旧思政字段生成初始学科知识-思政知识匹配
INSERT INTO subject_ideology_matches (subject_knowledge_id, ideology_knowledge_id, is_primary, match_score, match_reason)
SELECT
    sk.id,
    ik.id,
    1,
    95.00,
    COALESCE(NULLIF(kp.ideological_value, ''), 'Migrated from legacy knowledge point ideological value.')
FROM knowledge_points kp
JOIN subject_knowledge sk ON sk.name = kp.name AND sk.subject = kp.subject
JOIN ideology_knowledge ik ON ik.name = '工匠精神'
WHERE kp.node_type = 'TECH' AND kp.ideological_value IS NOT NULL AND kp.ideological_value <> '';

