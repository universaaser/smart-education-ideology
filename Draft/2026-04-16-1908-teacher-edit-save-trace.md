# Teacher Edit Save Trace Closure

## 结论
本次完成了“上传结果可编辑、可保存、可追溯”的后端与前端最小闭环，已达到下一轮导出能力接入前的稳定契约目标。导出功能按计划延期，当前不影响教师保存与版本回读主链路。

## 改动原因
- 需要把上一阶段结构化流水线结果落地成教师可持续编辑的数据模型，而不是停留在一次性生成结果。
- 与 `/Draft/毕设.md` 的对应关系：属于“智能教学内容生成”从 demo 生成走向可运营闭环的关键基础能力。

## 具体改动
- 修改文件：
  - `backend/src/main/java/com/smartedu/entity/TeachingMaterial.java`
  - `backend/src/main/java/com/smartedu/mapper/TeachingMaterialMapper.java`
  - `backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`
  - `backend/src/main/java/com/smartedu/controller/UploadController.java`
  - `backend/src/main/java/com/smartedu/controller/TeachingMaterialController.java`
  - `backend/src/main/java/com/smartedu/dto/TeachingMaterialDraftDto.java`
  - `backend/src/main/java/com/smartedu/dto/TeachingMaterialSaveRequestDto.java`
  - `backend/src/main/java/com/smartedu/dto/TeachingMaterialViewDto.java`
  - `backend/src/main/java/com/smartedu/dto/TeachingTraceItemDto.java`
  - `backend/src/main/resources/migration_teaching_materials.sql`
  - `services/api.ts`
  - `views/ResourceUpload.tsx`
  - `backend/src/test/java/com/smartedu/controller/UploadControllerTest.java`
- 影响接口：
  - 新增 `GET /api/upload/tasks/{taskId}/editor-draft`
  - 新增 `PUT /api/upload/tasks/{taskId}/editor-draft`
  - 新增 `POST /api/upload/tasks/{taskId}/materials`
  - 新增 `GET /api/materials/{materialId}`
- 数据结构：
  - 新增 `teaching_materials` 专用表与 `trace_json` 固定结构快照。

## 改动原因说明
- 采用专用表是为避免复用 `parse_tasks` 导致语义混杂，并为版本化保存留出稳定边界。
- 保持最小改动：保留现有上传入口与轮询接口，不改已有 `result-detail/regenerate` 语义，只补编辑保存旁路。

## 风险与注意事项
- 未完成项：导出（DOCX/Markdown）仍未实现。
- 潜在风险：`trace_json` 目前是 JSON 快照，适合回读展示，不适合复杂检索场景。
- 兼容性：旧上传/解析链路保持兼容；新接口依赖新增表，部署需执行迁移脚本。

## 验证情况
- 已执行：
  - `backend`: `mvn -q test` 通过
  - `backend`: `mvn -q -DskipTests package` 通过
  - `frontend`: `npm run build` 通过
- 未验证：
  - 生产数据库真实迁移执行
  - 多用户并发编辑同一 `parseTask` 的冲突策略

## 下一位 agent 的接手提示
- 下一步优先：补导出能力（Markdown/DOCX）并复用 `TeachingMaterialViewDto` 作为导出输入。
- 关键文件：
  - `backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`
  - `views/ResourceUpload.tsx`
  - `services/api.ts`
  - `backend/src/main/resources/migration_teaching_materials.sql`
- 当前卡点：追溯已可展示但不可高效检索，若要做按知识点/思政元素筛选，建议拆追溯关系表。
