# OpenAI Local Chat Model GPT-5.4

## 结论

已将本地 OpenAI 兼容接口的默认聊天模型从 `gpt-5.2` 调整为 `gpt-5.4`。手动探测确认 `http://localhost:8317/v1/chat/completions` 接受该模型请求。

## 改动原因

- 用户明确要求把默认模型改为 `gpt-5.4`。
- 该调整属于 AI 助手默认配置收口，仍对应 `/Draft/毕设.md` 中 AI 助手与多模型能力要求。

## 具体改动

- `backend/src/main/resources/application.yml`
  - 将 `ai.openai.model` 默认值从 `gpt-5.2` 改为 `gpt-5.4`

## 方案取舍

- 只改默认模型名，不改 provider、key、base-url，也不扩展新的模型探测逻辑，保持最小改动。
- 在实际写入配置前先手动探测本地接口，避免把默认值改成一个本地服务直接拒绝的模型。

## 风险与注意事项

- 当前本地接口虽然接受 `gpt-5.4` 请求，但返回体中的 `choices[0].message.content` 仍为 `null`。
- 因此本次完成的是默认模型切换，不代表 AI 助手返回格式兼容问题已经解决。

## 验证情况

- 手动探测 `http://localhost:8317/v1/chat/completions`
- 请求模型：`gpt-5.4`
- 结果：接口返回 `model: gpt-5.4`
- 补充观察：`choices[0].message.content` 当前仍为 `null`

## 下一位 agent 的接手提示

- 若聊天页面依旧报错，优先排查 `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java` 对响应体 `message.content` 非空的强假设。
- 若本地兼容服务支持返回标准 Chat Completions 文本内容，应优先在服务侧修正。
