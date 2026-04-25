# Student Learning Report

## 结论

本轮完成 PRD 第 1 包“学生学习行为采集与学习报告”的 demo 闭环，1.1-1.6 已达到完成边界；1.7 答题正确率因缺真实答题入口保持未做。

## 改动原因

- 对应 `/Draft/毕设.md` 中“学生自主学习监测与预警”的前置数据采集能力。
- PRD 要求先补学生行为与报告，为后续预警中心、反馈建议和路径推荐画像提供真实事件来源。

## 具体改动

- 后端新增 `student_activity_events`、迁移脚本、实体/Mapper、`StudentActivityEventService` 和 `StudentActivityEventController`。
- 新增 `POST /api/student/events`、`GET /api/student/report`、`GET /api/student/recent-activities`。
- 前端新增 `TrackingContext`，学生端接入页面停留、课程/材料打开、知识图谱节点查看、AI 提问埋点。
- `StudentHome` 展示今日学习时长、今日事件数、知识点访问数、7 日趋势和最近学习记录。
- 已同步 `Draft/PRD.md`、验收清单和 `CHANGE-INDEX.md`。

## 方案取舍

- 采用独立事件流表，不改旧 `student_activities` 预警表，避免破坏已有 `AlertService` 语义。
- 首页报告按学生汇总，不强制 courseId，避免学生首页没有选课上下文时出现空报告。
- 学生端资源库当前没有正式学生材料预览入口，因此展开课程也记录为资源打开事件，保证学生端有可演示入口。

## 风险与注意事项

- `TrackingContext` 默认 courseId 为 `1`；后续完成学生-课程绑定后应改为真实当前课程。
- 未接入答题入口，不能统计正确率。
- 本轮不包含教师预警中心、学生反馈建议、视觉采集或隐私授权。
- 仍需在真实浏览器中手动点击验证事件落库和报告刷新。

## 验证情况

- 已补 `StudentActivityEventControllerTest` 覆盖批量上报、空事件失败、学习报告和最近活动接口。
- 待执行后端定向测试与前端构建。

## 下一位 agent 的接手提示

- 下一步建议做 PRD 第 2 包“学生预警中心与学生反馈”。
- 可直接基于 `student_activity_events` 生成 `student_alert_records`，并在教师端新增 `AlertConsole`。
