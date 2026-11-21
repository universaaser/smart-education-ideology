# Dashboard 学习活动趋势查询性能优化(聚合下推数据库)

- 时间:2026-09-27 15:45
- 作者:王林睿(CS5351 Group 20,Sprint 1 性能组)
- 对应 Issue:#4
- 对应 PR:#6

## 与 `/Draft/毕设.md` 的关系

基础设施/辅助能力改进:不改变任何功能行为与验收状态,仅优化教师 Dashboard overview 接口中一处可复现的高成本查询。

## 改动说明

`DashboardService.buildActivityTrend()` 原先将最近 7 天活动事件**全量读入 JVM 内存**,用 Java 逐条分组计数;事件量增长后耗时线性上升。

改为通过 MyBatis-Plus `QueryWrapper.selectMaps` 生成 `DATE(occurred_at)` + `COUNT(*)` + `GROUP BY` 聚合 SQL,由数据库完成分组计数,最多返回 7 行。Java 侧保留原有"7 天逐日补 0"的拼装逻辑,对外返回结构完全不变(7 个 `{day, value}` 数据点,按日期升序)。

关键文件:
- `backend/src/main/java/com/smartedu/service/DashboardService.java`
- `backend/src/test/java/com/smartedu/service/DashboardServiceTest.java`
- `docs/sprint1/perf-measurement.md`(测量记录)
- `docs/sprint1/measure-before.txt`、`docs/sprint1/measure-after.txt`(原始日志)

## 测试与测试边界

定向测试 `shouldPushActivityTrendAggregationDownToDatabase` 断言:
1. 生成的 SQL 片段包含 `GROUP BY`(聚合确实下推);
2. 不再允许对事件表调用全量 `selectList`(防止回退);
3. 返回仍为最近 7 天、日期逐日连续升序;
4. 数据库返回的聚合值被正确映射(今天=3、前天=5),无事件日期补 0。

**测试边界**:测试使用动态代理 mock mapper,**不执行真实 SQL**,仅验证生成的 SQL 片段与结果映射逻辑;回归测试 `shouldBuildOverviewFromExistingModules` 原样通过(2 tests passed)。真实性能数字来自本机 MySQL 8 + 10 万行事件数据的手动测量,不依赖 mock。

## 测量结果(同一数据集,20 次请求平均)

`student_activity_events` 造数 100,000 行:

| 指标 | 改前 | 改后 |
|---|---|---|
| 平均耗时 | 3,213.2 ms | 72.4 ms(-97.7%,约 44 倍) |
| 最小 / 最大 | 3,067 / 3,575 ms | 59 / 206 ms |

## 已验证 / 未验证项与风险

- 已验证:`mvn -f backend/pom.xml -Dtest=DashboardServiceTest test` 通过;本机启动后端手动测量(见原始日志)。
- 未验证:其他成员环境复测;更大数量级(百万行)下的表现;`occurred_at` 索引未单独评估(当前数据量下全表扫描聚合已足够快,数据量再大可考虑加索引)。
- 风险:`DATE()` 依赖数据库时区,与本机 `LocalDate.now()` 不一致时跨日边界可能有偏差——当前部署为单机同库,风险可忽略,记录在案。
