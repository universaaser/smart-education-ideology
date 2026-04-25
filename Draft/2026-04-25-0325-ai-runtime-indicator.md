# AI Runtime Indicator

## 结论

完成 PRD 7.7“AI 助手运行时提示”状态收口：普通用户在 AI Assistant 页面可只读查看当前聊天运行时 provider、model 和 base URL。

## 改动原因

- 对应 `Draft/PRD.md` 第 7 包可选项 7.7。
- 避免用户误以为 AI Assistant 仍提供失真的多 provider 切换；当前运行时按项目口径收口到本地 OpenAI-compatible。

## 具体改动

- 代码审查确认 `views/AIAssistant.tsx` 已展示：`Local OpenAI-Compatible`、`gpt-5.4`、`http://localhost:8317/v1`。
- 同步 `Draft/PRD.md`：7.7 从 `[TODO]` 改为 `[DONE]`，并追加第 7 包实现记录。
- 同步验收清单中 AI 运行时入口与展示条目。
- 更新 `Draft/CHANGE-INDEX.md`。

## 方案取舍

- 不新增后端接口读取 DB route；当前页面显示的是本地演示运行时常量，符合当前完成边界“能展示当前 chat provider/model”。
- 不恢复 DeepSeek/Gemini 切换，避免与当前统一本地 OpenAI-compatible 的演示口径冲突。
- 本轮不改代码，只做状态收口和文档同步。

## 风险与注意事项

- 管理员 Model Settings 中保存的 DB route 目前不承诺即时驱动 AI Assistant 运行时显示。
- 真实多 provider 全链路联调仍保持 PRD 7.8 `[TODO]`。
- 仍需浏览器手动确认顶部标签和空态文案实际显示效果。

## 验证情况

- 代码审查：`views/AIAssistant.tsx` 顶部标签和空态说明均已包含 provider/model/base URL。
- `npm run build`：通过。
- 未执行后端测试：本轮未修改后端代码。

## 下一位 agent 的接手提示

如果继续打磨第 7 包，可考虑 7.8 真实多 provider 联调或把 Model Settings 的 DB route 接入运行时读取，但这会涉及后端运行时路由架构，不建议作为小改动顺手完成。
