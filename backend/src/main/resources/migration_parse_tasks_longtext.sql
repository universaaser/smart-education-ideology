-- 将 parse_tasks 的 parsed_content / ai_analysis 由 TEXT 扩容为 LONGTEXT。
-- 原因：AI 流水线生成的结构化 JSON（documentStructure + knowledgePoints + ideologyMatches + teachingArtifacts）
-- 对稍长文档极易超过 TEXT 的 64KB 上限。MySQL 严格模式下会抛 "Data too long for column"，导致：
--   1. 流水线尾部的 UPDATE 把任务置 COMPLETED/FAILED 失败；
--   2. processDocumentAsync 的 catch 用同一份巨 JSON 再次 UPDATE 又失败；
--   3. 任务永远停在 ANALYZING/80%，前端轮询死锁。
-- LONGTEXT 上限 4GB，足够覆盖任何合理体量的 pipeline JSON。

ALTER TABLE parse_tasks
    MODIFY COLUMN parsed_content LONGTEXT COMMENT 'MinerU解析后的结构化文本',
    MODIFY COLUMN ai_analysis   LONGTEXT COMMENT 'AI分析结果（思政融合建议）';
