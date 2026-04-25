# Upload Editor Follow-up Fix

## 结论

已完成上传完成页教学编辑体验的二次修复：`Cases`、`Options`、`Scoring Points` 输入行都改为占满 Remove 按钮外的剩余宽度；同时修复真实运行库 `teaching_material_traces` 缺少 `resource_title` 等资源引用列导致 `Save Draft` 返回 `Failed to save teaching material draft` 的问题。

## 改动原因

- 用户反馈上一轮后 `Cases` 仍未变宽，且 `Scoring Points`、`Options` 也需要填满整行。
- 用户点击 `Save Draft` 仍失败，后端报错为 `Unknown column 'resource_title' in 'field list'`。
- 根因是已有库中 `migration_material_trace_and_course_binding.sql` 已被记录/跳过，但旧 `teaching_material_traces` 表实际缺少资源引用扩展列。

## 具体改动

- `ParseResultCorrectionCard`：补齐 Teaching Artifacts 中 `Cases` 与 `Scoring Points` 的 flex 行布局；已改过的 `Chapter Outline`、`Teaching Focus` 也补 `minWidth: 0` 与按钮固定宽度，避免长内容挤压。
- `TeachingMaterialEditorCard`：补齐 `Options`、`Scoring Points` 的 flex 行布局，并强化 `Cases` 输入框宽度与 Remove 按钮固定宽度。
- `scripts/update_database.ps1`：收紧 `migration_material_trace_and_course_binding.sql` 的结构识别条件，要求 trace 资源引用列都存在才视为 schema 已满足。
- `migration_teaching_material_trace_resource_columns.sql`：新增幂等 repair migration，专门补齐 `resource_title`、`resource_source`、`resource_source_url`、`resource_quoted_excerpt`、`citation_explanation`。
- 已按项目规则执行数据库升级：先 dry-run，再正式执行；正式执行前自动备份到 `Draft/db-backups/smart_education_20260425_133810.sql`。

## 方案取舍

这次没有在 `TeachingMaterialService` 捕获并吞掉 trace 插入异常，因为那会掩盖数据库结构错误，也可能让保存成功但追溯数据丢失。采用 repair migration 是更直接且可验证的修复，保持服务端写入资源引用 trace 的既有设计不变。

## 验证情况

- `scripts/update_database.ps1 -User root -Password root -DryRun -MysqlPath "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe" -MysqldumpPath "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe"`：确认仅需执行 repair migration。
- `scripts/update_database.ps1 -User root -Password root -MysqlPath "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe" -MysqldumpPath "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe"`：执行成功，生成备份并记录 migration。
- 已核对 `schema_migrations`：`migration_teaching_material_trace_resource_columns.sql` 为 `executed`。
- 已核对 `teaching_material_traces`：`resource_title`、`resource_source`、`resource_source_url`、`resource_quoted_excerpt`、`citation_explanation` 均存在。
- `mvn -f backend/pom.xml test -Dtest=UploadControllerTest,TeachingMaterialServiceTest,AiIntelligenceServiceTest` 通过，46 tests / 0 failures / 0 errors。
- `npm run build` 通过。

## 仍需人工验收

- 启动前后端后，在浏览器上传或打开已有 `.md` 解析任务，确认 `Cases`、`Options`、`Scoring Points` 都按整行宽度展示。
- 点击 `Save Draft`，确认不再出现 `Unknown column 'resource_title'` 引发的 500。
- 对合法内容点击 `Save Version`，确认可保存；对非法选择题确认返回业务校验错误而不是 500。
