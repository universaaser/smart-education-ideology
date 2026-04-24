# Phase 0 + Phase 1 变更记录

## Phase 0：error_message 截断修复

**根因**：`AiIntelligenceService` 中 3 处 `setErrorMessage` 调用存在不一致裁剪（0/500/1000），而 `parse_tasks.error_message` 列仅 `VARCHAR(500)`，regenerate 路径直接写入未裁剪异常消息导致 `Data truncation`。

**改动**：
- `AiIntelligenceService.java`：新增 `recordTaskFailure(task, cause, warnings, step)` 统一写入，摘要裁至 480 字，完整栈走 `log.error`。
- 替换位置：`:913`（processDocumentAsync catch）、`:939`（regenerateTask catch）、`:1028`（stub fallback）。
- 迁移：`error_message VARCHAR(500) → VARCHAR(1000)`（`migration_parse_tasks_error_detail.sql`）。
- `schema.sql` 同步。

## Phase 1：LONGTEXT / TEXT → 原生 JSON

**改动**：

| 表 | 列 | 旧 | 新 |
|---|---|---|---|
| parse_tasks | parsed_content | LONGTEXT | JSON |
| parse_tasks | ai_analysis | LONGTEXT | JSON |
| teaching_materials | cases_json | TEXT | JSON |
| teaching_materials | questions_json | TEXT | JSON |
| teaching_materials | document_structure_json | TEXT | JSON |
| teaching_materials | knowledge_points_json | TEXT | JSON |
| teaching_materials | ideology_matches_json | TEXT | JSON |
| teaching_materials | trace_json | TEXT | JSON |

**新增**：
- `parse_tasks.gen_doc_title VARCHAR(300) VIRTUAL` 生成列 + `idx_gen_doc_title` 索引。

**迁移脚本**：`migration_json_type_upgrade.sql`（含脏数据清洗 + ALTER）。

**兼容性**：
- Java 实体字段仍为 `String`，MyBatis-Plus `StringTypeHandler` 对 JSON 列透明兼容。
- 所有写入点走 `writeJsonSafely()`（Jackson），始终产出合法 JSON。
- 迁移前脏数据检查：0 行异常。

## 验证

- `mvn compile -DskipTests` → exit 0。
- MySQL 列类型确认全部到位（`information_schema.COLUMNS` 验证）。
- 后续需做一次完整上传→解析→保存→导出流程回归。
