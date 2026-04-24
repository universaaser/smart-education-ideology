# 任务卡在 ANALYZING/80 的根因与修复

## 现象

上传较大文档（本次 `02-进阶架构与源站高可用.docx`，50KB）后任务停在
`status=ANALYZING, progress=80, current_step="Generating teaching artifacts..."`，
实时日志已打 `Pipeline finished for taskId=3`，前端 `pollTaskStatus` 每 2s 反复查 `WHERE id=3` 但永远看不到终态。

## 根因

`parse_tasks.parsed_content` 和 `parse_tasks.ai_analysis` 是 `TEXT`（上限 64KB）。
`AiIntelligenceService.runPipeline` 在 stage 全部回退到 stub 后仍然会：

1. `task.setAiAnalysis(writeJsonSafely(pipelineResult))` —— 把 `documentStructure + knowledgePoints + ideologyMatches + teachingArtifacts` 合并成的 JSON 放进 `task.aiAnalysis`；稍长一点的文档就会超过 64KB。
2. `parseTaskMapper.updateById(task)` —— MySQL 严格模式下抛 `Data too long for column 'ai_analysis'`。
3. 冒泡到 `processDocumentAsync` 的 catch。旧实现直接 `task.setStatus("FAILED"); parseTaskMapper.updateById(task)`，但 `task` 内存里依然带着那份巨型 `aiAnalysis`，**二次 UPDATE 同样失败**。
4. 异常被 `@Async` executor 吞掉，`finally` 里只有 `endTask` 写 live-log；DB 停在 step 1 之前最后一次成功写入，即 `updateTaskProgress(ANALYZING, 80, "Generating teaching artifacts...")`。

## 修复

### 1. 列类型扩容（真·根因）

- `@backend/src/main/resources/schema.sql`：`parsed_content` / `ai_analysis` 由 `TEXT` 改为 `LONGTEXT`（新库 fresh install 用）。
- 新增 `@backend/src/main/resources/migration_parse_tasks_longtext.sql`（存量库迁移，幂等 `ALTER TABLE ... MODIFY COLUMN`）。

LONGTEXT 上限 4GB，覆盖任何合理体量的 pipeline JSON。

### 2. 失败路径防御性修复

`@backend/src/main/java/com/smartedu/service/AiIntelligenceService.java` 的 `processDocumentAsync` catch：
从 DB 重新 `selectById(taskId)` 拿一个"纯净" task，仅翻 `status=FAILED / currentStep / errorMessage / updatedAt`
后落库。即使未来出现别的"内存态字段太大导致 UPDATE 失败"，终态也一定能写进去，任务不会再永远卡住。
外层再套一层 try/catch 把终态落库自身的异常记 log 而非外抛。

## 操作指引（用户侧）

1. **执行迁移**（存量数据库）：

   ```powershell
   mysql -u<user> -p<pass> <dbname> < backend\src\main\resources\migration_parse_tasks_longtext.sql
   ```

   若是全新部署直接跑 `schema.sql` 即可。

2. **清理卡住的脏数据**（可选）：

   ```sql
   UPDATE parse_tasks SET status='FAILED',
          error_message='Manually cleared: pipeline JSON exceeded TEXT limit before schema fix.',
          current_step='Pipeline failed'
   WHERE status='ANALYZING';
   ```

3. **重启后端**。

4. **重试上传**同一份 docx：任务应走完 `ANALYZING/90 → COMPLETED` 或 `ANALYZING/80 → FAILED`，不再卡在 80%。

## 验证

- `mvn -q -DskipTests compile` 通过。
- 需用户跑一次上传验证：预期前端 `pollTaskStatus` 能在合理时间内收到终态，后端停止对 `parse_tasks WHERE id=?` 的 2s 轮询回放。

## 未在本次范围

- 其它表的 `TEXT` 列未改（`courses.description`、`resources.content` 等），这些列本次没有证据触顶，留待需要时单独评估。
- AI 流水线 prompt/结果瘦身优化（例如控制 `lectureNotes` 长度、限制 `knowledgePoints` 条数）；LONGTEXT 已覆盖容量问题，瘦身属于质量优化独立任务。
