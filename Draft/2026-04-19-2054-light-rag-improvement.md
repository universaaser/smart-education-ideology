# Light RAG Improvement

## 结论

本次完成轻量 RAG 2.0 改进：AI 普通对话已从“关键词检索后拼 prompt”升级为 chunk 级检索、结构化引用返回和检索状态提示。选段解释继续复用统一检索入口。

## 改动原因

- 对应 `/Draft/毕设.md` 中知识库来源追溯和大模型交互增强目标。
- 原 AI 助手缺少普通聊天引用展示、无来源提示和片段级证据。

## 具体改动

- 新增 `knowledge_chunks` 实体、Mapper、构建服务和迁移脚本 `backend/src/main/resources/migration_light_rag_chunks.sql`。
- 改造 `KnowledgeRetrievalService`：优先 FULLTEXT 检索 chunk，结果不足时 LIKE 兜底，再回退旧知识点/资源检索。
- 改造聊天接口：`/api/chat/sessions/{sessionId}/message` 返回 `message`、`citations`、`retrievalStatus`。
- 前端 `views/AIAssistant.tsx` 展示检索状态和参考来源。
- 补充 chunk 构建、检索状态、聊天响应测试。

## 方案取舍

- 采用 MySQL FULLTEXT + LIKE fallback，未引入向量库和 embedding，符合当前轻量增强目标。
- 保留旧检索兜底，避免目标库未执行迁移或旧数据未重建 chunk 时完全无结果。

## 风险与注意事项

- 目标环境需要执行 `migration_light_rag_chunks.sql`。
- MySQL 中文 FULLTEXT 效果有限，已保留 LIKE fallback。
- 仍未实现向量召回、reranker 和 RAG 自动评估。

## 验证情况

- 已执行 `mvn -q test`，通过。
- 已执行 `npm run build`，通过；仍有既有前端 chunk 超过 500KB 警告。
- 未做浏览器手动点击验收。

## 下一位 agent 的接手提示

- 优先看 `KnowledgeRetrievalService`、`KnowledgeChunkService`、`ChatService` 和 `views/AIAssistant.tsx`。
- 若继续增强 RAG，下一步应接 embedding 或优化中文全文检索，而不是继续扩大 prompt 拼接。
