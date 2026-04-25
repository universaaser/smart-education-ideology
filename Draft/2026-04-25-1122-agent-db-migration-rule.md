# Agent DB Migration Rule

## 结论

已按用户要求更新 `AGENTS.md`：数据库迁移默认由 agent 自动执行，本地演示库用户和密码均固定为 `root`。

## 改动原因

- 这是项目执行规范补充，服务于 `/Draft/毕设.md` 相关功能在本地数据库上的持续可运行性。
- 用户明确要求把数据库脚本执行责任写入 `AGENTS.md`。

## 具体改动

- 新增 `AGENTS.md` 第 11 节“数据库迁移执行”。
- 明确默认数据库为 `smart_education`，用户 `root`，密码 `root`。
- 明确 agent 先 dry-run、再正式执行、再核对 `schema_migrations` 与关键结构。
- 明确迁移前必须备份，失败时必须说明脚本进度、数据库状态、备份位置和修复方案。

## 方案取舍

- 只更新 agent 行为规则，没有改动业务代码、迁移脚本或数据库结构。
- 不要求用户手工执行 SQL，但保留 MySQL bin 路径缺失时向用户获取路径的例外。

## 风险与注意事项

- 该规则适用于本地演示数据库；生产环境仍不应默认使用 `root/root`。
- 后续 agent 执行迁移时仍需先备份和 dry-run，不能直接跳过验证。

## 验证情况

- 已完成文档修改。
- 未执行数据库迁移、构建或测试；本次仅变更项目执行规范。

## 下一位 agent 的接手提示

- 如涉及数据库结构变化，直接使用 `scripts/update_database.ps1 -User root -Password root -DryRun` 和正式升级命令，不要让用户手工逐个执行迁移脚本。
