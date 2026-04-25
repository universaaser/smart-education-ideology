# Crawl Source Review

## 结论

本轮完成 PRD 第 4 包“知识源配置、抓取日志与人工审核”的必做 demo 闭环，4.1-4.6 已达到完成边界；4.7 定时调度仍保持可选 TODO。

## 改动原因

- 对齐 `/Draft/毕设.md` 的思政知识库来源追溯、更新与审核要求。
- 让现有资源抓取从纯后台 demo 变成教师端可配置、可触发、可查看日志、可审核入库的工作流。

## 具体改动

- 新增 `crawl_sources`、`crawl_run_logs` 及 `resources.review_status/reviewed_by/reviewed_at` 迁移与 schema 字段。
- 新增 `CrawlSourceController`、`CrawlSourceService`、来源/运行日志实体、Mapper 和请求 DTO。
- `ResourceCrawlService` 按启用来源过滤硬编码规则，支持指定来源触发，并在完成、停止、失败时记录运行日志。
- `ResourceService` 默认新资源为 `PENDING`，资源列表、分页资源和聊天上下文检索只返回 `APPROVED` 资源。
- 前端新增 `SourceManagement` 页面、菜单入口、API 类型和审核操作。

## 方案取舍

- 第一版仅管理来源名称、baseUrl、启用状态和备注，站点 CSS selector 等规则继续复用后端 `CrawlSiteRule`，避免把范围扩大成可视化爬虫配置器。
- 审核影响先落在资源列表和聊天检索入口，满足“至少一个真实入口受审核状态影响”的完成边界。

## 风险与注意事项

- 目标数据库仍需执行 `migration_crawl_sources_review.sql`。
- 自建 source 的 baseUrl 需要能匹配现有 `CrawlSiteRule.listUrl`，否则会记录一条失败运行日志但不会抓取到站点内容。
- 定时调度、失败重试、下架、重新同步、GDELT/arXiv 等扩展来源未做。
- 尚未做真实浏览器手动验收和真实外部站点联网抓取验收。

## 验证情况

- `mvn -f backend/pom.xml -Dtest=ResourceControllerTest,CrawlSourceControllerTest,ResourceCrawlServiceTest test`：14 tests，0 failures，BUILD SUCCESS。
- `npm run build`：通过。
- 代码审查后补充了审核状态非法值的 controller 边界校验，避免无效状态落到 service 异常。

## 下一位 agent 的接手提示

下一步按 PRD 顺序进入第 5 包“课程关键词采集任务”。如果继续打磨第 4 包，优先补浏览器手动验收、目标库迁移执行记录、定时调度和来源规则可视化，而不是扩大当前已完成包的范围。
