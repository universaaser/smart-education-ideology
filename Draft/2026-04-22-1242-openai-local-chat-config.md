# OpenAI Local Chat Config

## 结论

已将默认聊天链路切到本地 OpenAI 兼容接口 `http://localhost:8317/v1`，默认 provider 改为 `openai`，默认模型改为 `gpt-5.2`，API key 默认值改为 `your-api-key-1`。本地手动探测确认该接口接受 `gpt-5.2` 请求。

## 改动原因

- 对应 `/Draft/毕设.md` 中 AI 助手与多模型能力要求。
- 之前默认聊天走代理配置，实际返回模型/通道不支持，导致 AI 助手无法正常响应。
- 用户已提供新的本地兼容接口和 key，需要按此切换默认配置。

## 具体改动

- `backend/src/main/resources/application.yml`
  - 将 `ai.routing.default-chat-provider` 从 `proxy` 改为 `openai`
  - 将 `ai.routing.background-providers` 调整为 `openai,deepseek,proxy`
  - 将 `ai.openai.enabled` 改为 `true`
  - 将 `ai.openai.api-key`、`base-url`、`model` 默认值分别改为 `your-api-key-1`、`http://localhost:8317/v1`、`gpt-5.2`

## 方案取舍

- 采用最小改动，只调整后端默认配置，不改前端模型选择、不改聊天服务实现。
- 模型名采用官方当前新一代 GPT-5 家族中的 `gpt-5.2`，并以本地接口实际接受该模型请求为落地依据。
- 没有继续扩大到模型探测脚本、配置页面或环境变量模板整理，避免超出本次任务范围。

## 风险与注意事项

- 本地接口虽然接受 `gpt-5.2` 请求，但当前手动探测返回包中的 `choices[0].message.content` 为 `null`，说明聊天兼容格式可能仍有问题。
- 因此本次完成的是“默认配置切换”，不是“AI 助手链路彻底恢复”；若仍无法正常回复，下一步应排查本地兼容服务的返回格式或后端解析兼容性。
- 现有 `deepseek`、`proxy` 配置仍保留，未删除。

## 验证情况

- 手动探测 `http://localhost:8317/v1/chat/completions`
- 请求模型：`gpt-5.2`
- 结果：接口接受请求并返回 `model: gpt-5.2-2025-12-11`
- 补充观察：返回对象为 `chat.completion`，但 `choices[0].message.content` 当前为 `null`

## 下一位 agent 的接手提示

- 若 AI 助手仍失败，优先检查 `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java` 对 `choices[0].message.content` 的假设是否需要兼容本地服务返回。
- 若本地服务支持其他更稳定的 chat 模型，也可以继续探测后替换 `OPENAI_MODEL` 默认值。
