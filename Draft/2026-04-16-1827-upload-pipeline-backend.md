# Upload Pipeline Backend Closure

## 结论
本次完成“上传教材/大纲 -> 解析文档 -> 提取知识点 -> 匹配思政元素 -> 生成讲义/案例/题目”的后端结构化闭环，结果已可通过新接口稳定读取与重生成。未进入教师前端编辑与导出阶段，符合“先夯实后端根基”的范围。

## 改动原因
- 现有流程仅有上传+解析骨架，结果格式不可控，难以支撑后续编辑与验收。
- 本次与 `/Draft/毕设.md` 对应关系：属于“智能教学内容生成与课程设计辅助系统”的核心后端链路补强。

## 具体改动
- 修改 `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`：拆分四阶段流水线（解析/提取/匹配/生成），新增统一 JSON 校验、一次重试、受控 fallback、结构化落库与重生成能力。
- 新增 DTO：`DocumentStructureDto`、`KnowledgePointDto`、`IdeologyMatchDto`、`TeachingArtifactsDto`、`PipelineResultDto`。
- 新增 `backend/src/main/java/com/smartedu/service/AiPipelineJsonValidator.java`：统一字段白名单、必填项、长度与数组上限校验。
- 修改 `backend/src/main/java/com/smartedu/controller/UploadController.java`：新增 `GET /api/upload/tasks/{taskId}/result-detail` 与 `POST /api/upload/tasks/{taskId}/regenerate`。
- 修改 `backend/src/main/java/com/smartedu/service/KnowledgeIngestionService.java`：`ingestParseTask` 改为解析结构化 `parsed_content`/`ai_analysis` 生成资源内容、思政摘要与标签。
- 修改 `backend/src/main/resources/application.yml`：新增 `ai.pipeline.*` 配置。
- 修改 `services/api.ts`：补充新接口与结构化类型定义。
- 新增测试：JSON 校验器单测、服务层结果读取单测、上传控制器新接口测试。

## 改动原因说明
- 采用“复用 parse_tasks 字段存 JSON”而非新增表，避免本轮数据库迁移风险，符合最小改动原则。
- 先固定后端契约再做前端编辑，可避免前端反复适配不稳定结构。
- 未做大范围重构，保留原上传入口和任务轮询接口兼容性。

## 风险与注意事项
- 当前文档读取仍依赖文本读取回退，复杂 PDF/表格/公式解析能力有限。
- LLM 输出已受结构约束，但语义质量仍依赖模型与提示词。
- `regenerate` 为同步触发，后续高并发场景需评估线程池与限流。
- 本次未实现前端编辑保存导出，不应误标记为完整教学内容闭环完成。

## 验证情况
- 已执行：`mvn -q test`（通过，含新增测试）。
- 已执行：`mvn -q -DskipTests package`（通过）。
- 已执行：`npm run build`（通过，仍有前端 chunk > 500KB 警告）。
- 未验证：真实 LLM key 下的端到端语义质量、复杂文档样本、生产级并发稳定性。

## 下一位 agent 的接手提示
- 先看 `AiIntelligenceService` 的四阶段输出结构与 `PipelineResultDto`。
- 再看 `UploadController` 新增的 `result-detail` 与 `regenerate`，直接用于下一轮教师编辑器对接。
- 下一步优先：教师端编辑/保存闭环、结果来源证据展示、导出策略（Markdown/DOCX）与验收联调。
