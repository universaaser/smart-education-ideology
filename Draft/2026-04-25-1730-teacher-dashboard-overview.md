# Teacher Dashboard Overview

## 结论

已完成 `2026-04-25-1633-prd-demo-richness-plan` 的 P0-2 教师 Dashboard 聚合工作台最小闭环，教师第一屏可看到跨模块待办、材料发布、最近解析任务和学习事件趋势。

## 改动原因

- 对应 `/Draft/毕设.md` 中教师备课、知识库审核、学生监测与课程资源管理的综合展示需求。
- 原 Dashboard 主要是静态统计和课程表，难以体现已有模块闭环。

## 具体改动

- `DashboardService` 新增 `getOverview`，聚合待处理预警、待审核资源、待审核匹配、失败解析任务、最近解析任务、材料版本统计和近 7 日学习事件趋势。
- `DashboardController` 新增 `GET /api/dashboard/overview`。
- `views/Dashboard.tsx` 新增可跳转待办卡片、材料发布卡片和最近解析任务列表，并把 Open Alerts 跳转修正到 Alerts。
- `services/api.ts` 增加 Dashboard overview 类型和 API。
- 新增 `DashboardServiceTest`、`DashboardControllerTest`。

## 方案取舍

- 未新增数据库表，全部复用现有业务数据，符合最小改动。
- 未做复杂 BI 图表、实时推送或强一致统计，演示场景刷新加载即可。

## 风险与注意事项

- 部分聚合仍是全局口径，教师归属过滤只覆盖解析任务和材料版本，资源/匹配/预警的细粒度权限仍待后续权限体系完善。
- 本轮未做真实浏览器手动验收。

## 验证情况

- `mvn -f backend/pom.xml -DskipTests compile` 通过。
- `mvn -f backend/pom.xml -Dtest=DashboardServiceTest,DashboardControllerTest test` 通过。
- `npm run build` 通过。

## 下一位 agent 的接手提示

- 下一项建议继续做 `P0-3 章节-知识点-材料关联展示`。
- 若继续完善 Dashboard，优先补真实浏览器验收和教师/课程归属过滤，不要扩展成复杂 BI 平台。
