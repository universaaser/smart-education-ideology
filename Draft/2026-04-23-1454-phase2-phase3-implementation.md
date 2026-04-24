# Phase 2 + Phase 3A Implementation

## 完成内容

### Phase 2：结构化投影表 + FULLTEXT 扩展

- **新增表**：
  - `parse_task_knowledge_points`：拉平 pipeline 知识点结果，含 FULLTEXT 索引 `ft_ptkp_def`
  - `parse_task_ideology_matches`：拉平 pipeline 思政匹配结果，含 FULLTEXT 索引 `ft_ptim_reason`
- **新增实体 + Mapper**：
  - `ParseTaskKnowledgePoint` / `ParseTaskKnowledgePointMapper`
  - `ParseTaskIdeologyMatch` / `ParseTaskIdeologyMatchMapper`
- **AiIntelligenceService 改动**：
  - `runPipeline` 写完 `aiAnalysis JSON` 后调用 `persistPipelineProjections` 同事务内写入投影表
  - `regenerateTask` 调用 `deleteProjectionsByTaskId` 先删旧投影
  - `tryRebuildFromParsedContent` 同样写入投影表
- **存量回填**：`ProjectionBackfillService`（`CommandLineRunner`），启动时遍历 `parse_tasks.ai_analysis IS NOT NULL` 反序列化后回填投影表；若表已有数据则跳过
- **schema.sql / migration**：同步更新 `schema.sql` 和新建 `migration_phase2_projection_tables.sql`

### Phase 3A：Qdrant 向量检索层

- **配置** (`application.yml`)：
  - `qdrant.enabled` / `qdrant.host` / `qdrant.port`
  - `ai.embedding.enabled` / `provider` / `model` / `dimensions` / `base-url`
  - 默认全部关闭，用户按需开启
- **VectorIndexService**：
  - `embed` / `embedBatch`：调用 OpenAI-compatible `/embeddings` REST API
  - `upsert` / `searchByVector` / `search`：调用 Qdrant REST API（collection 自动创建，Cosine 距离）
  - 全部方法含优雅降级：Qdrant/embedding 不可用时返回空列表/null，不抛异常阻断主流程
- **语义搜索 API**：`SemanticSearchController`
  - `POST /api/semantic/search`：端到端语义搜索（embedding + qdrant search）
  - `GET /api/semantic/knowledge-points?taskId=`：按 parseTaskId 查投影表知识点
  - `GET /api/semantic/ideology-matches?taskId=`：按 parseTaskId 查投影表思政匹配
- **异步向量索引**：`AiIntelligenceService.asyncIndexToVectorStore`
  - Pipeline 完成后 `@Async` 异步写入 Qdrant
  - 索引 `knowledge_points` 和 `ideology_matches` 两个 collection
- **前端改动**：
  - `ResourceLibrary.tsx`：顶部新增语义搜索输入框（`Input.Search` + `CompassOutlined`），结果以 Card/List 展示在课程列表上方
  - `SelectionExplainPanel.tsx`：新增 "Similar Recommendations" 区域，基于当前解释答案调用 `semanticApi.search({ scope: 'selection_explain' })` 获取相似历史推荐

## 文件清单

| 文件 | 动作 |
|---|---|
| `backend/src/main/resources/schema.sql` | 追加两个投影表定义 |
| `backend/src/main/resources/migration_phase2_projection_tables.sql` | 新建：Phase 2 迁移脚本（含 teaching_materials FULLTEXT） |
| `backend/src/main/java/com/smartedu/entity/ParseTaskKnowledgePoint.java` | 新建 |
| `backend/src/main/java/com/smartedu/entity/ParseTaskIdeologyMatch.java` | 新建 |
| `backend/src/main/java/com/smartedu/mapper/ParseTaskKnowledgePointMapper.java` | 新建 |
| `backend/src/main/java/com/smartedu/mapper/ParseTaskIdeologyMatchMapper.java` | 新建 |
| `backend/src/main/java/com/smartedu/service/ProjectionBackfillService.java` | 新建 |
| `backend/src/main/java/com/smartedu/service/VectorIndexService.java` | 新建 |
| `backend/src/main/java/com/smartedu/controller/SemanticSearchController.java` | 新建 |
| `backend/src/main/java/com/smartedu/dto/SemanticSearchRequestDto.java` | 新建 |
| `backend/src/main/java/com/smartedu/dto/SemanticHitDto.java` | 新建 |
| `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java` | 修改：注入新 mapper + vector service；runPipeline/regenerate/tryRebuild 中写入投影表和向量索引 |
| `backend/src/main/resources/application.yml` | 修改：追加 qdrant + embedding 配置 |
| `services/api.ts` | 修改：新增 `SemanticSearchRequest` / `SemanticHit` / `semanticApi` |
| `views/ResourceLibrary.tsx` | 修改：新增语义搜索输入框和结果展示 |
| `components/SelectionExplainPanel.tsx` | 修改：新增 Similar Recommendations 区域 |

## 验证结果

- `mvn -q -DskipTests compile`：exit 0
- `npm run build`：exit 0

## 使用说明

1. **启用语义搜索**：启动 Qdrant（docker-compose 或本地二进制），然后在 `application.yml` 或环境变量中设置 `QDRANT_ENABLED=true` 和 `EMBEDDING_ENABLED=true`。若 embedding base-url 留空，则自动复用 `ai.openai.base-url`。
2. **存量数据**：首次启动时 `ProjectionBackfillService` 会自动回填已有 `ai_analysis` 到投影表；向量索引不会自动回填历史数据，仅对新 pipeline 生效。如需历史数据向量索引，需额外脚本或重新触发 regenerate。
3. **降级行为**：Qdrant 或 embedding 未启用时，语义搜索 API 返回空数组，前端展示空状态；不影响主链路。
