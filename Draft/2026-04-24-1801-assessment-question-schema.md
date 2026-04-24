# Assessment Question Schema

## 结论

本次完成考核题结构化字段的最小闭环：AI 生成、后端校验、教师编辑保存、预览和 Markdown 导出均支持题型、难度、选项、知识点 ID、参考答案和评分点。

## 改动原因

- 对齐 `/Draft/毕设.md` 中“自动生成考核题目、参考答案、评分要点”的正式交付要求。
- 承接 `pending-items-technical-plan` 7.2，先用既有 `questionsJson` 扩展字段，避免新增表和题库抽象。

## 具体改动

- 后端 `QuestionDto` 增加 `questionType/difficulty/knowledgePointId/options`，并保留旧三参构造器兼容测试与旧调用。
- `AiIntelligenceService` 更新题目生成提示词与 JSON 校验，规范题型和难度默认值。
- `TeachingMaterialService` 保存、发布和 Markdown 导出保留新字段，发布时拒绝无选项的选择题。
- 前端 `TeachingMaterialEditorCard`、`ResourceUpload`、`TeachingMaterialPreview`、`services/api.ts` 接通新字段编辑、提交和展示。

## 方案取舍

- 未新增数据库字段，沿用 `questionsJson`，降低迁移风险。
- 未实现题型比例和答案-选项一致性自动判定，避免一次性扩大为完整题库系统。

## 风险与注意事项

- `knowledgePointId` 当前只作为可选数值保存，未校验是否真实存在。
- 选择题只校验至少一个选项，未校验参考答案是否命中选项。
- 尚未做浏览器手动点击验收。

## 验证情况

- `mvn -f backend/pom.xml -Dtest=TeachingMaterialServiceTest,AiIntelligenceServiceTest test` 通过，22 个测试成功。
- `npm run build` 通过。

## 下一位 agent 的接手提示

- 下一步可继续 7.2：在 AI prompt 中加入题型比例要求，并在后端校验答案与选项一致性、知识点 ID 有效性。
- 若要提升体验，可在前端根据题型自动显示/隐藏选项区域并提供发布前本地提示。
