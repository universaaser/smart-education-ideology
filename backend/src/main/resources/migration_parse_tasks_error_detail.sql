-- =====================================================
-- Phase 0: error_message truncation fix
-- =====================================================
-- 背景：
--   原 parse_tasks.error_message 为 VARCHAR(500)，但调用方（regenerate 路径）
--   直接写入未裁剪的异常消息，长度超过 500 时触发
--   "Data truncation: Data too long for column 'error_message'"，导致任务
--   无法落地 FAILED 状态。
--
-- 处理：
--   1) 应用层统一通过 AiIntelligenceService.recordTaskFailure 写入，并裁剪到 480 字；
--   2) 列宽扩到 VARCHAR(1000)，为异常信息保留余量；完整栈交由应用日志。
--
-- 执行顺序：在 Phase 1 的 migration_json_type_upgrade.sql 之前执行。

ALTER TABLE parse_tasks
    MODIFY COLUMN error_message VARCHAR(1000) COMMENT '错误摘要（完整栈走应用日志）';
