# Database Update Script

## 结论

已新增 `scripts/update_database.ps1`，用于把长期未更新的已有 MySQL 数据库升级到当前仓库需要的结构。脚本支持 dry-run、自动备份、迁移记录表和基于 `information_schema` 的已应用迁移识别。

## 改动原因

- 与 `/Draft/毕设.md` 的关系：属于数据与部署基础设施补强，支撑已实现功能在旧数据库上运行。
- 当前迁移脚本数量较多，且部分脚本包含非幂等 `ADD INDEX/ADD COLUMN`，直接全部重跑有重复索引或重复列风险。

## 具体改动

- 新增 `scripts/update_database.ps1`：按依赖顺序处理迁移，已存在的结构登记为 `adopted`，缺失迁移才执行。
- 修正 `migration_subject_ideology_split.sql`：补齐 `subject_knowledge` / `ideology_knowledge` 的 `deleted` 字段，避免后续 `migration_light_rag_chunks.sql` 在旧库增量迁移时引用缺失列。
- 修正 MySQL warning 处理：脚本不再通过命令行参数传递密码，避免 `mysql: [Warning] Using a password on the command line interface can be insecure.` 被 PowerShell 当作异常中断。
- 修正 MySQL 8.0.40 兼容性：移除迁移中的 `ADD COLUMN IF NOT EXISTS` 用法，改为 `information_schema` + dynamic SQL 判断后执行。
- 更新 `README.md`：补充 dry-run、正式升级、MySQL bin 路径和 `-InitIfMissing` 说明。
- 更新验收清单维护记录。

## 方案取舍

- 只修补既有迁移中与后续脚本直接冲突的缺失列，没有重排或重写历史迁移。
- 未引入 Flyway/Liquibase 等新依赖，保持本地演示项目的最小复杂度。
- 脚本默认备份数据库，降低已有数据升级风险。

## 风险与注意事项

- 如果某个非幂等迁移曾经执行到一半失败，脚本可能无法自动判断所有半完成状态，需要按报错人工核对。
- `-Password` 仅在脚本进程内临时写入 `MYSQL_PWD` 后传给子进程；正式环境仍建议使用临时账号或本地安全方式。

## 验证情况

- 已连接本机 MySQL 8.0.40 的 `smart_education` 数据库执行正式升级。
- 脚本生成备份：`Draft/db-backups/smart_education_20260425_111946.sql`。
- `schema_migrations` 当前共 20 条记录：9 条 adopted，11 条 executed。
- 再次执行 `.\scripts\update_database.ps1 -User root -Password root -DryRun`，全部迁移均为 `skip`。
- 已核对新增关键表、`resources.review_status/reviewed_by/reviewed_at`、`subject_ideology_matches` 审核字段和 `teaching_materials.chapter_id` 已存在。

## 下一位 agent 的接手提示

- 若 dry-run 显示需要执行迁移，先确认 `Draft/db-backups` 可写且 MySQL 账号有 DDL 权限。
- 正式执行后再启动后端，并用学生、教师、管理员主线做一次浏览器验收。
