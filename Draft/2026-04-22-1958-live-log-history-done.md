# 上传解析 UI · 实时日志 + 历史记录（已完成）

接 `2026-04-22-1952-live-log-history-handoff.md`，本轮完成了所有剩余项。

## 最终变更清单

### 后端

- 新文件 `@e:\study\smart-education-ideology\backend\src\main\java\com\smartedu\service\AiStreamBuffer.java`：按 `taskId` 维护 64KB 上限的实时日志缓冲；`ThreadLocal` 绑定当前异步任务；对外 `beginTask/endTask/appendStage/appendChunk/read(offset)`。
- `@e:\study\smart-education-ideology\backend\src\main\java\com\smartedu\service\AiIntelligenceService.java`：
  - 注入 `AiStreamBuffer`。
  - `processDocumentAsync` try/finally 内 `beginTask/endTask`。
  - `executeStage` 每次 attempt + fallback 时 `appendStage`。
  - `readChatCompletionStream` 每个 `piece` 同步 `appendChunk`，把 SSE 增量转给前端。
- `@e:\study\smart-education-ideology\backend\src\main\java\com\smartedu\controller\UploadController.java` 新增 2 个端点：
  - `GET /api/upload/tasks/{id}/live-log?offset=N` → `{content, cursor, status, currentStep}`
  - `GET /api/upload/tasks?userId=U&page=1&size=20` → `PageResult<TaskSummary>`

### 前端

- `@e:\study\smart-education-ideology\services\api.ts`：
  - 新接口 `uploadApi.getLiveLog(taskId, offset)` 和 `uploadApi.listTasks(...)`。
  - 新类型 `ParseTaskListItem`。
- `@e:\study\smart-education-ideology\views\ResourceUpload.tsx`：
  - **移除** `Progress` 组件。
  - 上传中改为**深色代码框**（`<pre>`，maxHeight 280px，自动滚底）显示 LLM 实时文本；顶部一行小 `Spin` + 当前阶段 + 文件名。
  - 完成后除原 `Alert` 外新增 `notification.success({ message: 'Parsing completed', description: fileName })` 明确提示。
  - 新增 **Parse History** Card（位于课程绑定之上），`uploadApi.listTasks` 驱动；每条显示文件图标、文件名、状态标签、创建时间。
  - 点击 `Open`：`status=COMPLETED` 时调用既有 `loadTaskIntoEditor`，直接在编辑器加载该任务；已有的 Trace 面板自然显示"解析知识点 ↔ `subject_knowledge`"对应关系（由 `knowledgePointId` 连接）。
  - 完成时自动 `loadHistoryTasks()` 刷新列表。

## 验证

- `mvn -q -DskipTests compile` 通过。
- 前端无 lint 错误（TypeScript）。
- 运行时用户可核验：
  1. 上传 PDF → 实时日志框出现阶段标记（`>>> document-structure attempt 1`）和 LLM 输出。
  2. 完成时右上 `notification.success` 弹出，editor 自动加载，下方 Trace 表里 `knowledgePointId` 非空行即知识点对应关系。
  3. 刷新页面 → "Parse History" 列出最近 20 条；点 Open 可查看任意历史任务（无需选课程）。
  4. FAILED 任务点击时弹出错误详情 notification。

## 设计选择记录

- **为什么轮询不用 SSE**：现有架构已有 `getTaskStatus` 轮询机制，复用更简单；SSE 需要额外处理 CORS/断线重连，性价比低。800ms 间隔对用户感知的"实时"完全足够。
- **为什么 ThreadLocal**：`@Async processDocumentAsync` 整条流水线在单线程内顺序执行，`ThreadLocal<Long>` 避免在 7 层调用链里显式透传 taskId。若未来引入阶段并行需改写。
- **知识点对应关系未新建视图**：底层 `TeachingMaterialTrace.knowledgePointId`（由 `TeachingMaterialService.loadSubjectKnowledgeIdMap` 按名称匹配回填）+ 现有 Trace 表已满足需求，只加入口即可。
- **`progress` state 保留但不再渲染**：`pollTaskStatus` 仍写入以兼容 `loadTaskIntoEditor` 的 `setProgress(taskInfo.progress || 100)`，属于最小改动原则。

## 未做（明确范围外）

- 历史列表分页：当前一次拉 20 条，未分页；如需翻页可在 `List` 外加 `Pagination` 并改 `page/size`。
- 实时日志持久化：当前进程重启即丢；若需要可持久化到 `parse_task.live_log_path` 文件。不属于本轮目标。
- `AiStreamBuffer.buffers` 的 LRU/TTL：当前单任务 64KB 上限，长期运行理论 1000 条 = 64MB，可接受。
