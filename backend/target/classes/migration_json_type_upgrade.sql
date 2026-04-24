-- =====================================================
-- Phase 1: LONGTEXT / TEXT -> native JSON columns
-- =====================================================
-- 背景：
--   LLM 解析产物（MinerU 文档结构、Pipeline 结果、教案快照）一直被塞在
--   LONGTEXT / TEXT 列里，SQL 无法按字段查询，也无法加索引。
--   升级到原生 JSON 类型后：
--     1) 可以直接使用 JSON_EXTRACT / ->> / JSON_TABLE 做结构化查询；
--     2) 可以挂 STORED/VIRTUAL 生成列 + 索引（见文末示例）；
--     3) 存储开销略降：MySQL 对 JSON 采用紧凑二进制表示。
--
-- 执行顺序：在 Phase 0 的 migration_parse_tasks_error_detail.sql 之后执行。
-- 回滚：JSON -> LONGTEXT / TEXT 直接 MODIFY 回去即可，MySQL 会按 UTF-8 JSON 文本写回。
--
-- 注意：
--   任何历史脏行（非法 JSON）会导致 ALTER 失败。这里先把非法行置 NULL，
--   保守处理；生产环境建议先 dump 再清洗。

-- ---------- parse_tasks ----------
UPDATE parse_tasks SET parsed_content = NULL
WHERE parsed_content IS NOT NULL AND JSON_VALID(parsed_content) = 0;

UPDATE parse_tasks SET ai_analysis = NULL
WHERE ai_analysis IS NOT NULL AND JSON_VALID(ai_analysis) = 0;

ALTER TABLE parse_tasks
    MODIFY COLUMN parsed_content JSON COMMENT 'MinerU 解析后的结构化文本',
    MODIFY COLUMN ai_analysis   JSON COMMENT 'AI 分析结果（思政融合建议）';

-- 文档标题投影列 + 索引：前端列表检索常按文件/课题名过滤，这里先满足这一场景。
-- 其他 JSON 字段投影留给 Phase 2 的独立投影表。
ALTER TABLE parse_tasks
    ADD COLUMN gen_doc_title VARCHAR(300) GENERATED ALWAYS AS
        (JSON_UNQUOTE(JSON_EXTRACT(parsed_content, '$.title'))) VIRTUAL,
    ADD INDEX idx_gen_doc_title (gen_doc_title);

-- ---------- teaching_materials ----------
UPDATE teaching_materials SET cases_json = NULL
WHERE cases_json IS NOT NULL AND JSON_VALID(cases_json) = 0;

UPDATE teaching_materials SET questions_json = NULL
WHERE questions_json IS NOT NULL AND JSON_VALID(questions_json) = 0;

UPDATE teaching_materials SET document_structure_json = NULL
WHERE document_structure_json IS NOT NULL AND JSON_VALID(document_structure_json) = 0;

UPDATE teaching_materials SET knowledge_points_json = NULL
WHERE knowledge_points_json IS NOT NULL AND JSON_VALID(knowledge_points_json) = 0;

UPDATE teaching_materials SET ideology_matches_json = NULL
WHERE ideology_matches_json IS NOT NULL AND JSON_VALID(ideology_matches_json) = 0;

UPDATE teaching_materials SET trace_json = NULL
WHERE trace_json IS NOT NULL AND JSON_VALID(trace_json) = 0;

ALTER TABLE teaching_materials
    MODIFY COLUMN cases_json              JSON COMMENT 'editable cases in json array',
    MODIFY COLUMN questions_json          JSON COMMENT 'editable questions in json array',
    MODIFY COLUMN document_structure_json JSON COMMENT 'snapshot of document structure json',
    MODIFY COLUMN knowledge_points_json   JSON COMMENT 'snapshot of knowledge points json',
    MODIFY COLUMN ideology_matches_json   JSON COMMENT 'snapshot of ideology match json',
    MODIFY COLUMN trace_json              JSON COMMENT 'trace information json';
