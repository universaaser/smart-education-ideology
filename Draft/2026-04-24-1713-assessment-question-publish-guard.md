# Assessment Question Publish Guard

## 结论

本次完成教学内容生成 P0 质量项中的题目完整性最小闭环：正式发布材料版本时，不再允许题干或参考答案为空的考核题进入发布版本；草稿阶段仍可编辑，但保存/读取会过滤不完整题目。

## 改动原因

- 对齐 `/Draft/毕设.md` 中“生成考核题目、参考答案、评分要点”的正式教学内容交付要求。
- `pending-items-technical-plan` 7.2 要求题目结构约束，本轮先落地不依赖新表和外部模型的基础字段完整性。

## 具体改动

- `TeachingMaterialService`：发布前新增题目完整性校验；`safeQuestionList` 统一丢弃缺少题干或参考答案的题目，保护草稿与旧数据读取。
- `TeachingMaterialServiceTest`：新增发布拒绝不完整题目、草稿过滤不完整题目的回归测试。
- 移除 `AiIntelligenceService`、`PathRecommendService`、`ResourceCrawlService` 文件开头 BOM，仅为恢复 javac 编译，不改变业务语义。

## 方案取舍

- 未新增题型枚举、选项 schema 或数据库字段，避免把 7.2 全量展开为大改。
- 发布选择直接拒绝而非静默过滤，避免教师以为题目已发布但实际丢失。

## 风险与注意事项

- Controller 层目前会按现有异常处理返回错误，前端发布失败提示仍可继续优化。
- 仍未覆盖题型分布、选项、难度、知识点 id 等完整 schema。

## 验证情况

- 已执行：`mvn -f backend/pom.xml -Dtest=TeachingMaterialServiceTest test`。
- 结果：9 个测试通过。
- 未验证：浏览器端点击发布不完整题目的真实提示文案。

## 下一位 agent 的接手提示

- 下一步可继续 7.2：扩展 `QuestionDto` 或新增题目结构 DTO，覆盖题型、选项、答案、难度和知识点 id，再同步前端编辑器。
