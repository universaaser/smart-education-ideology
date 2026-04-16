# Course Trace Rollback Closure

## 结论
本次完成教师链路剩余闭环：课程绑定、追溯可检索、版本回退为草稿三项能力均已落地，并保持现有上传/解析/重生成/Markdown 导出接口兼容。

## 改动原因
- 对齐 `/Draft/毕设.md` 中教师端“可编辑、可追溯、可回退”的闭环要求。
- 对齐本轮用户范围：不做 DOCX，不接 MinerU，优先补齐现有链路剩余能力。

## 具体改动
- 后端：
  - `UploadController`：`POST /api/upload/file` 支持可选 `courseId`；新增任务追溯检索、版本回退接口。
  - `TeachingMaterialController`：新增版本维度追溯检索接口。
  - `AiIntelligenceService`：流水线阶段注入课程上下文（课程知识点定义+思政价值）。
  - `TeachingMaterialService`：保存草稿/发布版本时同步写入结构化追溯行；支持任务/版本检索；支持历史版本回退为草稿。
  - 新增实体/Mapper/DTO：`TeachingMaterialTrace`、`TeachingMaterialTraceMapper`、`TeachingMaterialTraceDto`。
  - 迁移脚本：`migration_material_trace_and_course_binding.sql`（`parse_tasks.course_id`、`teaching_materials.course_id`、`teaching_material_traces`）。
- 前端：
  - `services/api.ts`：补充上传携带 `courseId`、追溯检索、版本回退 API。
  - `views/ResourceUpload.tsx`：上传页课程选择、追溯筛选区、版本回退按钮与回退状态提示。

## 改动原因说明
- 采用“最小改动”策略：复用现有上传任务与材料服务，不替换框架、不引入新依赖。
- 追溯采用“`trace_json` + 结构化行”双轨，兼容旧数据并支持检索分页。

## 风险与注意事项
- 未完成：DOCX 导出、高级追溯分析、并发编辑冲突控制。
- 风险：数据库需执行迁移脚本；旧历史材料依赖懒同步补齐追溯行。

## 验证情况
- 已执行：`mvn -q test`（backend）通过。
- 已执行：`mvn -q -DskipTests package`（backend）通过。
- 已执行：`npm run build`（frontend）通过。
- 未执行：真实数据库迁移后联调回归（需目标环境验证）。

## 下一位 agent 的接手提示
- 先看：
  - `backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`
  - `backend/src/main/java/com/smartedu/controller/UploadController.java`
  - `views/ResourceUpload.tsx`
- 下一步建议：
  1. 增加 DOCX 导出；
  2. 为版本发布加入并发冲突控制；
  3. 扩展追溯检索统计维度。
