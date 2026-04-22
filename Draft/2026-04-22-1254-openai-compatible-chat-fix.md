# OpenAI Compatible Chat Fix

## 结论

已为 AI 助手补上 OpenAI 兼容协议下的新模型调用兼容层：对 `gpt-5*` 等新模型优先尝试 `/v1/responses`，失败后回退 `/v1/chat/completions`，并兼容多种正文提取结构；前端也改为展示后端真实错误。`mvn -q test` 与 `npm run build` 已通过。

## 改动原因

- 用户明确说明当前接口是 OpenAI 兼容协议 API，要求修复兼容问题。
- 本地联调发现：`gpt-5.4` 在 `/chat/completions` 下会返回 `message.content = null`，单纯换模型名无法恢复聊天。

## 具体改动

- `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`
  - 为新模型增加 `/responses` 优先、`/chat/completions` 回退的双通道调用逻辑
  - 对 `chat.completions` 补 `max_completion_tokens` 兼容
  - 新增多种响应正文提取与错误消息解析
- `views/AIAssistant.tsx`
  - 不再吞掉后端错误，直接展示真实失败原因
- `backend/src/test/java/com/smartedu/service/AiIntelligenceServiceTest.java`
  - 新增兼容提取与路由策略回归测试

## 方案取舍

- 没有重写整条聊天链路，也没有扩展模型配置页面，只在现有服务层补兼容分支和解析兜底，保持最小改动。
- 没有把 `reasoning_content` 当作正文强行展示，避免把不应暴露的内容误当最终答复。

## 风险与注意事项

- 当前本地兼容服务即使走 `/responses` 和 `/chat/completions`，仍可能返回空正文；这属于上游服务行为，不是当前客户端解析逻辑可以完全补救的。
- 因此本次修复解决的是“客户端兼容能力不足”和“错误不可见”，不是保证任意上游实现都一定产出正文。

## 验证情况

- `cd backend; mvn -q test`
- `npm run build`
- 手动探测 `http://localhost:8317/v1/responses` 与 `http://localhost:8317/v1/chat/completions`
- 结果：代码构建与测试通过；本地服务仍存在空正文响应现象

## 下一位 agent 的接手提示

- 若 AI 助手仍不出字，优先抓取本地兼容服务完整原始响应，确认是否存在正文被放在非常规字段，或由服务侧直接修复空输出问题。
- 重点文件：`backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`、`views/AIAssistant.tsx`。
