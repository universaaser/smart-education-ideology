# Upload Parsed Content JSON Fix

## 结论

已修复资源上传 Markdown/纯文本类文档后，后台把原文直接写入 MySQL JSON 列 `parse_tasks.parsed_content` 导致 `Invalid JSON text` 的问题。修复后数据库保存合法 JSON，前端任务状态接口仍返回可渲染的 Markdown 文本。

## 改动原因

- `/Draft/毕设.md` 对应文档上传、结构化解析、教学内容生成链路。
- 近期迁移将 `parse_tasks.parsed_content` 升级为 JSON 列，但解析流程仍写入原始 Markdown/文本，上传 `.md` 文件会触发数据库截断错误。

## 具体改动

- `AiIntelligenceService`：解析早期与最终落库时均写入 `DocumentStructureDto` JSON；原始 Markdown/文本存放到 `rawMarkdown`。
- `UploadController`：`/api/upload/tasks/{taskId}` 返回前从 JSON 中提取 `rawMarkdown`，保持前端展示接口兼容。
- `UploadControllerTest`、`AiIntelligenceServiceTest`：补充 JSON 写库与 Markdown 返回回归断言。
- 未新增/删除业务文件；未修改数据库结构或接口字段名。

## 方案取舍

采用“JSON 列保存结构对象 + 状态接口展开原文”的最小修复，避免回退数据库迁移或改前端渲染协议；同时保留旧数据为非 JSON 文本时的返回兼容。

## 风险与注意事项

- 已覆盖后端定向测试；尚未在真实浏览器中重新上传 `.md` 文件手动验收。
- `parsedContent` 内部存储语义从原文文本收口为结构 JSON，依赖原始 DB 字段直读的外部脚本需改读 `rawMarkdown`。

## 验证情况

- `mvn -f backend/pom.xml test -Dtest=UploadControllerTest,AiIntelligenceServiceTest` 通过，25 tests / 0 failures / 0 errors。

## 下一位 agent 的接手提示

- 若继续验收，请启动后端和前端，在资源上传页上传 `.md` 文件，确认任务不再失败，完成后 Parsed Content Summary 能显示 Markdown 正文。
