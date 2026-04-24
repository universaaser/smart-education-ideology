# Resource Citation in Pipeline

## 结论

已完成“教材解析引用 Resource 摘录”第一阶段闭环：LLM 解析上下文会拼接课程相关 Resource 摘录，知识点/思政匹配结果可返回资源引用，教学材料 trace 与 Markdown 导出也能展示来源标题、链接、摘录和引用解释。

## 改动原因

- 当前教材解析只依赖上传文档正文，未利用数据库中已有爬虫资源。
- 用户明确要求在 LLM 解析时引用相关 Resource，并在解析结果中出现引用解释。
- 第一版已按确认边界实现为“资源摘录/摘要引用”，不要求高保真全文原文。

## 具体改动

- `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`
  - 在 `buildCourseContext(...)` 中追加相关 Resource 摘录上下文。
  - 扩展知识点提取/思政匹配 prompt、validator、fallback。
  - 投影写入新增 `resource_citations_json` / `citation_explanation`。
- `backend/src/main/java/com/smartedu/dto/ResourceCitationDto.java`
  - 新增资源引用 DTO。
- `backend/src/main/java/com/smartedu/dto/KnowledgePointDto.java`
  - 新增 `resourceCitations`。
- `backend/src/main/java/com/smartedu/dto/IdeologyMatchDto.java`
  - 新增 `citationExplanation`、`resourceCitations`。
- `backend/src/main/java/com/smartedu/service/ParseTaskCorrectionService.java`
  - 同步支持校正稿里的新 citation 字段，并同步 projection 写入。
- `backend/src/main/java/com/smartedu/dto/TeachingTraceItemDto.java`
  - 新增 Resource 标题、来源、链接、摘录、引用解释字段。
- `backend/src/main/java/com/smartedu/entity/TeachingMaterialTrace.java`
  - 新增对应 trace 落库字段。
- `backend/src/main/java/com/smartedu/dto/TeachingMaterialTraceDto.java`
  - 返回结构同步新增 citation 字段。
- `backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`
  - trace 构造时提取主引用；Markdown 导出追加 Resource 信息展示；trace 行落库与 DTO 映射同步。
- `backend/src/main/java/com/smartedu/entity/ParseTaskKnowledgePoint.java`
  - 新增 `resourceCitationsJson`。
- `backend/src/main/java/com/smartedu/entity/ParseTaskIdeologyMatch.java`
  - 新增 `citationExplanation`、`resourceCitationsJson`。
- `backend/src/main/resources/schema.sql`
  - 主 schema 同步新增 projection citation 字段。
- `backend/src/main/resources/migration_phase2_projection_tables.sql`
  - 为已有库补 projection 增量字段。
- `backend/src/main/resources/migration_material_trace_and_course_binding.sql`
  - 为已有库补 teaching material trace 的 citation 字段。
- `backend/src/test/java/com/smartedu/service/TeachingMaterialServiceTest.java`
  - 扩展测试样例，验证 trace/Markdown 中的 Resource 展示。
- `backend/src/test/java/com/smartedu/service/AiIntelligenceServiceTest.java`
  - 跟随构造参数变化修正 stub。
- `backend/src/test/java/com/smartedu/service/ParseTaskCorrectionServiceTest.java`
  - 跟随 correction 引用字段校验和投影逻辑保持通过。
- `backend/src/test/java/com/smartedu/service/ChatServiceTest.java`
- `backend/src/test/java/com/smartedu/controller/TeachingMaterialControllerTest.java`
  - 跟随 `AiIntelligenceService` 构造参数调整测试 stub。

## 方案取舍

- 采用最小改动：直接复用 `ResourceService.searchForChatContext(...)`，并把结果拼进现有 `courseContext`。
- 不单独新增复杂检索层，也不改动文档结构解析阶段。
- 不把“高保真原文全文”作为本次前置目标，只做资源摘录/摘要引用，以满足当前需求边界。

## 风险与注意事项

- 当前 Resource 检索仍是 like 检索，命中质量取决于课程知识点关键词。
- `resources.content` 并非总是完整原文，因此当前字段语义是“摘录/引用片段”，不是全文逐字引用。
- 数据库需要执行新的 migration，已有库否则缺字段。

## 验证情况

- 已执行：`mvn -f backend/pom.xml -DskipTests compile`
- 已执行：`mvn -f backend/pom.xml -Dtest=AiIntelligenceServiceTest,TeachingMaterialServiceTest,ParseTaskCorrectionServiceTest,ChatServiceTest,TeachingMaterialControllerTest test`
- 结果：通过。
- 未执行：真实浏览器端上传教材 -> 查看解析结果与 trace 的手动验收；真实数据库迁移执行验证。

## 下一位 agent 的接手提示

- 先看 `AiIntelligenceService.java` 中 `buildCourseContext(...)`、`validateKnowledgePoints(...)`、`validateIdeologyMatches(...)`、`persistPipelineProjections(...)`。
- 再看 `TeachingMaterialService.java` 中 `buildTraceItems(...)`、`buildMarkdown(...)`、`syncTraceRows(...)`。
- 若后续要升级到“高保真原文引用”，需要从 `resources` 存储策略和爬虫入库链路继续扩展，而不是只改当前 DTO。