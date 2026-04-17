# Selection Explain Closure

## 结论
本次完成教师材料页“选中文本 -> 知识库检索 -> AI 解释 -> 来源引用 -> 历史留存 -> 复制/插入讲义”闭环，达到本轮预期。

## 改动原因
- 对应 `/Draft/毕设.md` 中“上传一本书后，根据选中内容从知识库获取知识点进行解释”的目标。
- 验收清单第六部分此前缺少前端入口、来源分层和历史留存。

## 具体改动
- 修改 `ChatController`、`ChatService`，扩展选段解释结构化请求/响应，并新增历史分页查询。
- 新增 `SelectionExplainRecord`、mapper、request/evidence/history DTO 和 `migration_selection_explain_records.sql`。
- 修改 `services/api.ts`、`views/ResourceUpload.tsx`，接入教师材料页选段解释、证据展示、历史、复制和追加讲义草稿。
- 修改验收清单和 `CHANGE-INDEX.md`。

## 改动原因说明
- 复用现有 `KnowledgeRetrievalService` 与教师材料页，避免新建阅读器或全站划词能力。
- 历史仅保存解释文本和证据 JSON，是当前闭环所需的最小数据结构。

## 风险与注意事项
- 目标环境需要执行新增迁移脚本，否则历史入库会失败。
- 学生端阅读场景和独立收藏库未接入。
- 真实解释质量依赖模型 provider 配置和知识库命中质量。

## 验证情况
- 已执行 `mvn -q test`，通过。
- 已执行 `npm run build`，通过；仍存在既有前端 chunk 超过 500KB 警告。
- 未进行浏览器手动上传和真实模型联调。

## 下一位 agent 的接手提示
- 优先看 `views/ResourceUpload.tsx` 的 `Explain Selection` 区域和 `ChatService.explainSelection`。
- 部署前执行 `backend/src/main/resources/migration_selection_explain_records.sql`。
- 下一步可补学生端阅读页接入或独立收藏库。
