# Student Alert Feedback

## 结论

本轮完成 PRD 第 2 包“学生预警中心与学生反馈”的 demo 闭环，2.1-2.6 已达到完成边界；视觉演示模式和真实摄像头关键帧保持未做。

## 改动原因

- 对应 `/Draft/毕设.md` 中“学生自主学习监测与预警”的教师处理与学生反馈闭环。
- 第 1 包已具备 `student_activity_events`，本轮将行为事件转化为可处理预警和学生建议。

## 具体改动

- 后端新增 `student_alert_records`、迁移脚本、实体/Mapper/DTO 和 `AlertController`。
- 扩展 `AlertService`，新增 `POST /api/alerts/evaluate`、`GET /api/alerts/summary`、`GET /api/alerts`、`PUT /api/alerts/{id}/status`、`GET /api/student/feedback`。
- 前端新增 `alertApi`、`AlertConsole`，教师菜单增加 `Student Alerts`，支持汇总、筛选、详情展开、生成预警和状态处理。
- `StudentHome` 展示学生反馈建议，并提供跳转 AI 助手和知识图谱入口。
- 已同步 `Draft/PRD.md`、验收清单和 `CHANGE-INDEX.md`。

## 方案取舍

- 采用独立预警记录表，不复写旧 `student_activities`，保留旧三维预警算法语义。
- 预警规则基于最近 7 日行为事件做 demo 级判断，并对未处理同类型预警去重，避免重复生成噪声。
- 暂不做 WebSocket、复杂画像详情或视觉模型，优先完成可演示闭环。

## 风险与注意事项

- 需要在目标数据库执行 `migration_student_alert_records.sql`。
- 预警规则仍是行为事件规则，未接真实答题正确率、摄像头、专注度或情绪模型。
- 教师列表目前前端分页，后端固定返回最多 100 条；正式场景仍需后端分页。
- 仍需在真实浏览器中手动验证教师生成/处理预警和学生首页反馈刷新。

## 验证情况

- 已执行 `mvn -f backend/pom.xml -Dtest=AlertControllerTest,StudentActivityEventControllerTest test`，10 个用例通过。
- 已执行 `npm run build`，前端构建通过。

## 下一位 agent 的接手提示

- 下一步建议做 PRD 第 3 包“课程章节、知识点绑定与资源状态汇总”。
- 若继续打磨预警，可优先补后端分页、学生反馈已读/关闭状态、学生画像详情页和真实答题入口。
