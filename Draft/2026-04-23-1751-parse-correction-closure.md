# Parse Correction Closure

## 结论

已完成解析结果人工校正闭环：教师可在上传解析完成后修改完整结构化结果，保存最新校正稿，基于原文件重新解析，失败任务也可直接重试。自动验证 `mvn -q test` 与 `npm run build` 均通过。

## 改动原因

- 用户明确要求优先补齐“解析结果人工校正、保存、重新解析、失败重试”。
- 对应 `/Draft/毕设.md` 中教师上传文档后需可交互编辑 AI 结果、形成可用教学内容的主链路。

## 具体改动

- 新增 `parse_task_corrections` 表、实体、mapper、migration，按 `parse_task_id` 保存最新校正稿并用 `ACTIVE/STALE` 标记有效性。
- 新增 `ParseTaskCorrectionService`，负责校正稿读写、结构校验、旧稿失效、projection/vector 同步。
- `UploadController` 新增 `/correction-draft`、`/reparse`、`/retry`；`AiIntelligenceService` 支持同任务重跑/重试；`TeachingMaterialService` 改为读取有效结构化结果。
- 前端新增 `ParseResultCorrectionCard`，上传页可编辑文档结构、知识点、思政匹配和教学产物；失败页和历史记录支持 Retry。

## 方案取舍

- 只保留每个任务一份最新校正稿，不做多版本历史，避免预支复杂度。
- 重跑/重试复用原 `parseTaskId`，旧校正稿标记 `STALE`，不自动套用到新解析结果。
- 现有 `teaching_materials` 继续作为下游教学材料快照，不被校正保存静默覆盖。

## 风险与注意事项

- 目标数据库需执行 `backend/src/main/resources/migration_parse_task_corrections.sql`。
- 未做真实浏览器上传、校正、重跑、重试手点；当前结论基于自动化测试和构建。
- 真实 MinerU 或等价解析服务仍未接入，复杂 PDF/图片/公式解析能力仍受现有文本抽取限制。

## 验证情况

- `mvn -q test` 通过。
- `npm run build` 通过。

## 下一位 agent 的接手提示

- 优先看 `ParseTaskCorrectionService`、`UploadController`、`ParseResultCorrectionCard`、`ResourceUpload`。
- 下一步建议做浏览器端手动验收，并在目标库执行新增 migration 后验证保存校正稿与重跑/重试。
