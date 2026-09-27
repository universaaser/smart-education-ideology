# 性能测量:/api/dashboard/overview 学习活动趋势查询优化

- **测量人**:王林睿
- **测量日期**:2026-09-27
- **对应 Issue**:#1
- **改动范围**:`DashboardService.buildActivityTrend()`(仅此一个方法)

## 测量环境

| 项 | 值 |
|---|---|
| 机器 | Windows 11 笔记本(本机 localhost 测量) |
| JDK | Eclipse Adoptium OpenJDK 25.0.4 |
| 数据库 | MySQL 8.0(本机,root@localhost) |
| 数据规模 | `student_activity_events` 表 **100,000 行**(造数据脚本,近 7 天事件) |
| 测量方法 | 对 `GET /api/dashboard/overview` 连续请求 20 次,取平均(脚本:`04-测量脚本.ps1`) |

## 结果(20 次请求)

| 指标 | 改前 | 改后 | 变化 |
|---|---|---|---|
| 平均耗时 | **3,213.2 ms** | **72.4 ms** | **↓ 97.7%(约 44 倍)** |
| 最小耗时 | 3,067 ms | 59 ms | — |
| 最大耗时 | 3,575 ms | 206 ms | — |

原始记录:`measure-before.txt`(改前)、`measure-after.txt`(改后)。

## 改动说明

| 维度 | 改前 | 改后 |
|---|---|---|
| SQL | `SELECT * FROM student_activity_events WHERE occurred_at >= ?`(全列全行拉回 JVM) | `SELECT DATE(occurred_at) AS day, COUNT(*) AS value ... GROUP BY DATE(occurred_at)`(聚合下推数据库) |
| 数据库 → JVM 传输 | 7 天内全部事件行(本次测量约 10 万行) | 最多 7 行聚合结果 |
| 内存计数 | Java 逐条循环分组 | 不需要 |
| 返回给前端的结构 | 7 个 `{day, value}` 数据点 | **完全一致**(由定向测试保证) |

数据量越大,差距越大;传输行数与事件总量解耦后,该接口耗时不随事件量线性增长。

## 正确性验证

1. **定向测试**:`DashboardServiceTest#shouldPushActivityTrendAggregationDownToDatabase`
   - 断言 SQL 包含 `GROUP BY`(聚合确在数据库完成);
   - 断言不再调用 `selectList` 全量读取(调用即失败);
   - 断言返回仍为最近 7 天、计数来自数据库聚合结果。
2. **回归测试**:`DashboardServiceTest#shouldBuildOverviewFromExistingModules` 原样通过(2 tests passed)。
3. **接口抽查**:改后 `GET /api/dashboard/overview` 返回 `code:200`,`activityTrend` 为 7 个 `{day, value}` 数据点,结构不变。
