# Review Round 3 Lazy DTO

## 结论

本轮继续按“严格评审 + 最小改动”收口剩余质量问题，已完成后端剩余弱类型请求体清理、聊天发送接口 DTO 化、`ResourceUpload` 进一步拆分，以及前端懒加载拆包。功能逻辑未改，后端测试和前端构建均通过，前端 500KB chunk 告警已消除。

## 改动原因

- 该轮属于 `/Draft/毕设.md` 目标实现过程中的质量加固和维护性优化，不新增业务功能。
- 上一轮仍残留控制器 `Map` 请求体、超大前端组件和单大包构建风险，继续保留会提高接口演进成本和前端首屏负担。

## 具体改动

- 后端接口契约：
  `backend/src/main/java/com/smartedu/controller/ChatController.java` 改为 `ChatMessageRequestDto`；
  `backend/src/main/java/com/smartedu/controller/KnowledgeController.java` 改为 `NodePositionUpdateRequestDto`；
  `backend/src/main/java/com/smartedu/controller/ResourceController.java` 改为 `ResourceManualCrawlRequestDto`；
  新增对应 DTO 与 `KnowledgeControllerTest`、`ResourceControllerTest`。
- 前端可维护性：
  `views/ResourceUpload.tsx` 抽出 `components/SelectionExplainPanel.tsx`、`components/TeachingMaterialEditorCard.tsx`，文件从 1398 行降到 963 行。
- 前端构建：
  `App.tsx`、`layouts/TeacherShell.tsx`、`layouts/StudentShell.tsx` 改为路由/视图级懒加载；
  `vite.config.ts` 仅保留明确独立的 vendor chunk。
- 后端工程化：
  `backend/src/main/java/com/smartedu/SmartEducationApplication.java` 改用标准日志输出启动信息。

## 方案取舍

- 没有继续做更大范围的 service/页面重构，只优先处理高收益、低行为风险的问题。
- `ResourceController` 的兼容接口仍保留可选请求体，但改为空 DTO 并忽略未知字段，避免继续使用 ad-hoc `Map`。
- 没有直接修改 CORS 全开放策略，因为这会引入部署假设变化；本轮仅保留为审查风险项。

## 风险与注意事项

- `views/ResourceUpload.tsx` 虽已显著缩小，但仍是 963 行组件，后续应继续拆上传编排与数据刷新逻辑。
- `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`、`TeachingMaterialService.java` 仍然过大，职责边界偏宽。
- `backend/src/main/java/com/smartedu/config/CorsConfig.java` 仍使用 `addAllowedOriginPattern("*") + setAllowCredentials(true)`，生产环境存在明显安全风险。
- 未做浏览器端手动回归。

## 验证情况

- `backend`: `mvn -q test` 通过。
- `frontend`: `npm run build` 通过。
- 构建结果显示 `ResourceUpload`、`ResourceLibrary` 等已拆为独立 chunk，且未再出现 500KB 告警。

## 下一位 agent 的接手提示

- 优先继续审查并拆分 `views/ResourceUpload.tsx` 与 `views/ResourceLibrary.tsx` 的状态和副作用逻辑。
- 其次评估 `AiIntelligenceService`、`TeachingMaterialService` 的职责拆分切口，但避免一次性大改。
- 若开始处理部署安全，先从 `backend/src/main/java/com/smartedu/config/CorsConfig.java` 和配置模板着手。
