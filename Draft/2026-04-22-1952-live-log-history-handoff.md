# 上传解析 UI 改造 · 交接说明

## 用户需求（原话）

> 上传时的进度条去掉，改成一个高度适中的框显示 LLM API 处理的实时输出；处理完成要有明确的提示；同时完成后要能在不选择课程的情况下查看解析结果，具体做法可以在解析界面增加一个历史解析记录，在里面要能够查看文档解析后和当前数据库知识点的对应情况。当前系统内应该有相关的实现，查找看看做法是否合理，如果不合理可以进行大幅度改动。

## 现状盘点（已调研结论）

- **进度条位置**：`@e:\study\smart-education-ideology\views\ResourceUpload.tsx:913-925` 使用 `Spin + Progress`，由 `pollTaskStatus` 每 2 秒轮询 `GET /api/upload/tasks/{id}` 获取 `progress`/`currentStep`。
- **课程绑定**：当前已经是 **Optional**，未选课程照样可以完成解析并查看 editor；但没有"历史任务列表"入口，离开页面/刷新后就没地方重新打开旧任务。
- **知识点对应关系已有实现**：`TeachingMaterialTraceInfo.knowledgePointId`（`@e:\study\smart-education-ideology\services\api.ts:652-663`）在 `TeachingMaterialService.loadSubjectKnowledgeIdMap`（`@e:\study\smart-education-ideology\backend\src\main\java\com\smartedu\service\TeachingMaterialService.java:529-563`）里通过 `subject_knowledge.name` 精确匹配回填，当前只在编辑器卡片里以追溯表形式展示。**结论：底层数据已存在，前端只缺"从历史列表进入查看"的入口**，不需要重写。
- **LLM 流式**：后端 `readChatCompletionStream` 已经按 SSE 读 `delta.content`，但**从未把增量转发到前端**。前端现在只能看到阶段名。

## 本轮已完成改动（后端，未验证编译通过）

1. **新文件** `@e:\study\smart-education-ideology\backend\src\main\java\com\smartedu\service\AiStreamBuffer.java`
   - 按 `taskId` 维护 `StringBuilder` 缓冲区（64KB 上限，自动截断）。
   - 用 `ThreadLocal<Long>` 记录当前异步任务，避免在调用链每层透传 taskId。
   - 对外方法：`beginTask / endTask / appendStage / appendChunk / read(taskId, offset) → LiveLogSlice(content, cursor) / discard`。

2. **`AiIntelligenceService` 挂钩**（`@e:\study\smart-education-ideology\backend\src\main\java\com\smartedu\service\AiIntelligenceService.java`）
   - 构造注入 `AiStreamBuffer aiStreamBuffer`（字段见 line ≈61）。
   - `processDocumentAsync` try-finally 中 `beginTask(taskId)` / `endTask()`。
   - `executeStage` 每次 attempt 前 `appendStage(stageName + " attempt " + attempt)`；全兜底时 `appendStage("... fallback used (stub)")`。
   - `readChatCompletionStream` 在每个非空 `piece` 处 `aiStreamBuffer.appendChunk(piece)`。

3. **`UploadController` 准备工作**（`@e:\study\smart-education-ideology\backend\src\main\java\com\smartedu\controller\UploadController.java`）
   - 已注入 `private final AiStreamBuffer aiStreamBuffer;`
   - 已 `import` `AiStreamBuffer`、`com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper`、`com.baomidou.mybatisplus.extension.plugins.pagination.Page`，但**尚未添加实际端点**（未使用的 import 会产生警告但不影响编译）。

## 待完成（按优先级）

### 后端（两个端点，约 40 行）

写在 `UploadController` 末尾（`getFileExtension` 私有方法之前）：

1. **实时日志增量拉取**
   ```
   GET /api/upload/tasks/{taskId}/live-log?offset=0
   → { content: string, cursor: int, status: string }
   ```
   - `AiStreamBuffer.LiveLogSlice slice = aiStreamBuffer.read(taskId, offset);`
   - 组装 Map 返回，附上 `task.getStatus()` 让前端判断是否停止轮询。

2. **解析任务历史列表**
   ```
   GET /api/upload/tasks?userId={uid}&page=1&size=20
   → PageResult<TaskListItem>
   ```
   - 用 `LambdaQueryWrapper<ParseTask>` + `new Page<>(page, size)` + `parseTaskMapper.selectPage(...)`。
   - 条件：`userId` 可选；排序 `orderByDesc(ParseTask::getCreatedAt)`。
   - 返回字段（最小集）：`taskId, fileName, status, progress, currentStep, courseId, createdAt, completedAt, errorMessage`。
   - **无需新建 DTO**，直接组装 Map 返回即可（与现有 `getTaskStatus` 风格一致）。

### 前端（约 150-200 行）

1. **`@e:\study\smart-education-ideology\services\api.ts`** 新增：
   - `uploadApi.getLiveLog(taskId, offset) → { content, cursor, status }`
   - `uploadApi.listTasks({ userId, page, size }) → PageResultInfo<ParseTaskListItem>`
   - 新增类型 `ParseTaskListItem`（字段同上面后端返回）。

2. **`@e:\study\smart-education-ideology\views\ResourceUpload.tsx`** 改造：
   - **移除** `Progress` import 和 `<Progress percent={progress} status="active" />` 用法（line ≈923）。
   - **新增** 实时日志 state：`liveLog: string`, `liveLogCursor: number`。
   - **新增** 轮询：status 为 `uploading`/`parsing` 时每 800ms 调 `getLiveLog`，`offset = liveLogCursor`；把新 `content` 拼到 `liveLog` 后，更新 cursor。
   - **替换 UI**：在原 Progress 位置放一个固定高度（建议 `maxHeight: 280, overflow: auto`）的 `<pre>`，`whiteSpace: pre-wrap`，字号 12-13，使用等宽字体，`ref` 绑定后在每次 update 时自动 `scrollTop = scrollHeight`。
   - **完成提示**：status 变为 `completed` 时调用 `notification.success({ message: 'Parsing completed', description: taskResult.fileName, duration: 4 })`（已有的 `Alert` 保留）。
   - **新增 Card**"Parse History"：
     - 放在现有 `Intelligent Material Parsing` 标题之下、`Course Binding` 之上。
     - 用 `Table` 或 `List`：列 fileName、status（成功/失败标签）、createdAt、操作按钮 "Open"。
     - Open → 调 `uploadApi.getTaskStatus(taskId)`；如果 `status === 'COMPLETED'` 则复用已有的 `loadTaskIntoEditor(taskInfo)`。失败任务给出只读的 `errorMessage` 提示。
     - 知识点对应关系直接复用 editor 卡片里的 Trace 面板即可——`loadTaskIntoEditor` 已经会拉 traces，不需要新组件。

## 验证

- 后端：`mvn -q -DskipTests compile` 应通过（当前编译状态**未验证**，下一 agent 先跑一次）。
- 端到端：
  1. 上传 PDF → parse 阶段看到 LLM chunk 实时出现在日志框中。
  2. 完成后 → 右上角 `notification.success` 弹出，editor 加载正常。
  3. 刷新页面 → "Parse History" 列出历史任务；点 Open 可重新加载某个旧任务的 editor + traces（其中 `knowledgePointId != null` 的行即是与 `subject_knowledge` 的对应关系）。

## 注意事项

- **不要再起新的 handoff 文档**：本次任务应在 3-4 次编辑内完成；完成后合并到一份 `Draft/YYYY-MM-DD-HHMM-live-log-history-done.md`。
- **ThreadLocal 前提**：Spring `@Async` 为每个任务分配独立线程且顺序执行整条流水线，`AiStreamBuffer` 的 ThreadLocal 设计成立。若未来引入子线程并行阶段，需要重新评估。
- **内存**：`AiStreamBuffer.buffers` 当前只在单任务 64KB 上限内截断，但 `Map` 本身不会自动清理历史任务；可接受（每条任务最多 64KB，1000 条也就 64MB）。后续需要可以加 TTL 或 LRU。
- **AGENTS.md 约束**：代码内英文标识符 + 中文注释；不改无关代码；先给结论。
