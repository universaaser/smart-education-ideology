# AI Unified OpenAI Runtime

## 结论

按当前用户要求，项目内所有 AI 功能已统一收口到本地 OpenAI 兼容接口 `http://localhost:8317/v1`，默认模型固定为 `gpt-5.4`。本次同时修正了本地代理只在流式 `chat.completions` 下返回正文的问题，避免各条 AI 链路重复白跑空结果 `/responses`。

## 改动原因

- 对应 `/Draft/毕设.md` 中 AI 对话、文档解析、教学内容生成、知识提取等能力。
- 现状与用户要求冲突：仓库仍保留多 provider 入口，但用户明确要求“AI 相关功能都使用这个接口”。

## 具体改动

- `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`
  - 将聊天和后台 AI 链路统一归一到 `openai` 运行时配置。
  - 对 `localhost` OpenAI 兼容接口跳过 `/responses`，直接使用流式 `/chat/completions`。
- `backend/src/main/resources/application.yml`
  - 默认关闭 `proxy`、`deepseek`，默认聊天 provider 保持 `openai`。
- `views/AIAssistant.tsx`
  - 去掉误导性的 DeepSeek/Gemini 切换，前端直接展示固定运行时信息。
- `backend/src/test/java/com/smartedu/service/AiIntelligenceServiceTest.java`
  - 补充本地接口跳过 `/responses` 与 legacy provider 归一到 `openai` 的回归测试。

## 方案取舍

- 没有继续扩展新的模型配置页，而是先保证所有 AI 处理链路行为一致。
- 没有删除旧 provider 代码，只把运行时入口统一到 `openai`，保留后续恢复正式多模型能力的兼容壳层。

## 风险与注意事项

- 这次是按用户当前要求主动弱化多 provider demo，不代表正式多模型能力已完成。
- 未做连库后的逐页面手点回归，上传解析、资源抓取、学习路径建议等链路仍需端到端人工验收。

## 验证情况

- 实测 `http://localhost:8317/v1/models` 含 `gpt-5.4`。
- 实测 `localhost` 上 `/responses` 返回空 `output`、非流式 `/chat/completions` 返回 `content:null`、流式 `/chat/completions` 可返回 `delta.content` 正文。
- `backend`: `mvn -q test` 通过。
- `frontend`: `npm run build` 通过。

## 下一位 agent 的接手提示

- 若后续要恢复正式多模型能力，先看 `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java` 的统一路由与本地流式分支。
- 若要补 UI 侧配置能力，优先从 `views/AIAssistant.tsx` 和环境变量模板入手，而不是直接恢复失真的 provider 下拉。
