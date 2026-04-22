# openai-stream-chat-fix

## 结论

将 `AiIntelligenceService` 的 chat.completions 调用改为 SSE 流式并聚合 `delta.content`，解决本地 OpenAI 兼容代理 `http://localhost:8317/v1` 在非流式下返回 `content:null` 导致前端对话、选段解释、文档解析全部拿不到 AI 回复的问题。配置保持 `openai` 为默认链路，模型保留旗舰 `gpt-5.4`。

## 改动原因

- 实测 `http://localhost:8317/v1/chat/completions` 与 `/responses`（API Key=`your-api-key-1`）在非流式模式下 `choices[0].message.content` / `output` 恒为空，仅 `stream:true` 时通过 `choices[0].delta.content` 分片返回正文。
- 现有 `AiIntelligenceService.callChatCompletionsApi` 依赖非流式 JSON，得到空内容就抛 `empty assistant content`，所有 AI 入口（ChatController、SelectionExplain、文档流水线、extractIdeologicalValue）随之失败。
- 对应 `/Draft/毕设.md` 的 AI 对话与文档智能解析目标。

## 具体改动

- `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`
  - import 增加 `ResponseBody`、`BufferedReader`。
  - `callChatCompletionsApi` 请求体固定 `stream:true`，请求头加 `Accept: text/event-stream`。
  - 新增 `readChatCompletionStream(ResponseBody)`：按行解析 SSE，拼接 `choices[0].delta.content`；遇到 `[DONE]` 结束；畸形 chunk 静默跳过；空结果仍按原有语义抛错以触发熔断/回退。
  - 保留 `extractChatCompletionContent` 与 `callResponsesApi` 原实现，避免影响已熔断回退链与已有测试。
- 未修改 `application.yml`：`default-chat-provider=openai`、`ai.openai.base-url=http://localhost:8317/v1`、`api-key=your-api-key-1`、`model=gpt-5.4` 已符合要求。

## 方案取舍

- 为何统一走流式而不加开关：该代理仅流式有内容，官方/DeepSeek 均原生支持流式，行为一致；新增开关属于未被要求的配置面扩张。
- 为何不移除 `/responses` 分支：保留原有 `shouldPreferResponsesApi` 回退，当接入真正的 OpenAI 官方 responses API 时仍可用；当前代理 responses 返回空会自然回退到新的流式 chat.completions。
- 为何不改模型名：`gpt-5.4` 即用户所述“最新模型”的旗舰款，已在 `/v1/models` 列表中。

## 风险与注意事项

- 只在本地代理上做过端到端流式 curl 验证；未在真实 DeepSeek / 官方 OpenAI 账号上再次验证（历史已验证流式可用）。
- `extractChatCompletionContent` 现在仅由单元测试使用，功能未删以满足 AGENTS “不清理无关代码”。
- 若后端运行环境的 OkHttp 代理或反向代理禁止 SSE（中断 chunked），将退化为空内容→抛错→熔断。遇此现象需要在运维侧放行 `text/event-stream`。

## 验证情况

- `mvn -Dtest=AiIntelligenceServiceTest test`：5/5 通过（含 `shouldExtractChatCompletionTextFromArrayContent` 等反射测试）。
- `curl` 实测 `http://localhost:8317/v1/chat/completions` 流式返回 `delta.content` 正文，非流式返回 `content:null`——确认根因与修复路径。
- 未启动完整后端进行端到端对话验证（依赖 MySQL 环境）；建议启动后在 AIAssistant 面板发一条消息回归。

## 下一位 agent 的接手提示

- 关键文件：`backend/src/main/java/com/smartedu/service/AiIntelligenceService.java` 的 `callChatCompletionsApi` 与 `readChatCompletionStream`。
- 若后续换回非流式服务商，需要把 `stream:true` 改回条件开关，并重新启用 `extractChatCompletionContent` 路径。
- 若出现“empty assistant content from chat.completions”，先用 `curl --no-buffer -N` 确认代理是否仍只在流式下返回 `delta.content`。
