# Course AI Graph Review

## 结论

已审查 `2026-04-25-1430-course-ai-graph-report` 涉及改动，并完成两处最小健壮性修复：AI Assistant 历史侧栏布局下输入区不会再落入侧栏列；学生 Learning Report 最近活动在 SQL 查询阶段排除 `page_stay`，避免页面停留流水挤掉真实学习记录。已达到本轮“保证系统正常运行与健壮性”的代码层要求。

## 改动原因

- 对应 `/Draft/毕设.md` 中 AI 助教、学生学习支持闭环。
- 原报告已说明待验证，本次审查发现构建能过但运行体验和查询口径仍有风险。

## 具体改动

- `views/AIAssistant.tsx`：为双列 grid 增加行定义，侧栏跨两行，消息区和输入区固定在右侧聊天列。
- `StudentActivityEventService`：最近活动查询增加 `eventType != page_stay`，过滤前移到数据库查询。
- 新增 `StudentActivityEventServiceTest` 覆盖最近活动查询口径。

## 方案取舍

- 未重构 AI Assistant 组件，只修正 grid 定位，保持现有视觉与交互结构。
- 未改变学习报告 DTO 和接口协议，只收紧查询条件，避免影响前端调用方。

## 风险与注意事项

- 仍未做真实浏览器手动验收；AI 标题质量仍依赖模型输出。
- 课程接口级权限、课程字段枚举约束仍属于原报告已列出的后续增强项。

## 验证情况

- `mvn -q "-Dtest=StudentActivityEventServiceTest,StudentActivityEventControllerTest,CourseControllerTest,AiIntelligenceServiceTest" test` 通过。
- `mvn -q test` 通过。
- `npm run build` 通过。

## 下一位 agent 的接手提示

- 如继续验收，优先手动点击 AI Assistant 历史会话/输入框布局、学生首页 Learning Report 最近活动、Dashboard 进入 Course Management。
