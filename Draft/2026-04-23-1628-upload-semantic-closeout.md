# Upload Semantic Closeout

## 结论

本轮完成上传解析稳定化和 Phase 2/3A 语义检索的收口，代码、测试、文档和验收清单已同步。上传历史/实时日志、Markdown 结果展示、语义搜索与选段解释相似推荐不再停留在半成品状态。

## 改动原因

- 对齐 `/Draft/毕设.md` 中“文档解析、教学内容生成、知识检索”的主链路。
- 解决现有代码里测试基座断裂、`selection_explain` 只搜不写索引、`application.yml` 重复顶层键等阻塞问题。

## 具体改动

- `ChatService`：保存新 `SelectionExplainRecord` 后异步写入 `selection_explain` 向量索引。
- `VectorIndexAsyncService`：新增选段解释索引入口；仅处理新记录，不回填历史。
- `application.yml`：合并重复 `ai` 顶层配置。
- 测试：补 `UploadControllerTest` 的 `/tasks`、`/live-log`，新增 `SemanticSearchControllerTest`、`VectorIndexServiceTest`，修复 `AiIntelligenceServiceTest`、`TeachingMaterialControllerTest`、`TeachingMaterialServiceTest`、`ChatServiceTest` 的构造依赖。

## 方案取舍

- 只补新生成解释的向量索引，不做历史 `selection_explain_records` 回填，避免扩大本轮范围。
- 不把上传历史接口改成 DTO，继续沿用现有 `Map<String,Object>` 结构，保持最小改动。

## 风险与注意事项

- 未做真实 Qdrant 联机回归，当前结论基于后端测试与前端构建。
- 聊天主 RAG 仍未切到向量检索；局部语义检索仅覆盖课程资源库和新选段解释记录。
- 按用户 2026-04-23 最新要求，DOCX 导出已从当前需求中移除；MinerU 正式接入和历史解释回填仍待下一轮。

## 验证情况

- 自动化：`mvn -q test`、`npm run build` 通过。
- 手动验收脚本：
  1. 上传 `pdf/docx`，确认文件保存成功、实时日志持续追加、任务最终不再卡在 `ANALYZING/80`。
  2. 上传完成后检查 `Parse History` 出现新任务，点击 `Open` 能回填编辑器，`Parsed Content` / `AI Analysis` 以 Markdown 正常展示，Markdown 导出文件可渲染。
  3. 启用 `QDRANT_ENABLED=true` 与 `EMBEDDING_ENABLED=true` 后，在课程资源库输入语义查询，确认返回知识点结果；连续生成两条相近选段解释，第二条出现相似推荐。
  4. 关闭 Qdrant 或 embedding 后，语义搜索应为空结果或友好提示，上传主链路不受影响。

## 下一位 agent 的接手提示

- 若继续完善教师主线，优先做模板化排版或更细粒度人工校正，而不是补 DOCX 导出。
- 若继续完善语义检索，下一步应补历史 `selection_explain_records` 回填和聊天主链路向量化。
- 关键入口：`backend/src/main/java/com/smartedu/service/ChatService.java`、`backend/src/main/java/com/smartedu/service/VectorIndexAsyncService.java`、`backend/src/test/java/com/smartedu/controller/SemanticSearchControllerTest.java`。
