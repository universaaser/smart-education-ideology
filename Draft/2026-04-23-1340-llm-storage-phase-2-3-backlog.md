# Phase 2 / Phase 3 待办（冻结计划）

本文档归档 LLM 解析产物可查询性重构的后续阶段，供新上下文窗口接手。

---

## Phase 2：结构化投影表 + FULLTEXT 扩展

### 目标
把 LLM 产物中"会被当作业务实体查询"的字段从 JSON 里拉平到关系表，获得原生 SQL 索引与聚合能力。

### 新增表

```sql
CREATE TABLE parse_task_knowledge_points (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parse_task_id BIGINT NOT NULL,
    course_id BIGINT,
    point_name VARCHAR(200) NOT NULL,
    definition TEXT,
    chapter VARCHAR(200),
    evidence_snippet TEXT,
    pipeline_version VARCHAR(20),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task (parse_task_id),
    INDEX idx_course_point (course_id, point_name),
    FULLTEXT INDEX ft_point_def (point_name, definition, evidence_snippet)
);

CREATE TABLE parse_task_ideology_matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parse_task_id BIGINT NOT NULL,
    knowledge_point_name VARCHAR(200) NOT NULL,
    ideology_element VARCHAR(120) NOT NULL,
    match_reason TEXT,
    pipeline_version VARCHAR(20),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task (parse_task_id),
    INDEX idx_ideology (ideology_element),
    FULLTEXT INDEX ft_reason (match_reason)
);
```

### 代码改动点
- `AiIntelligenceService.runPipeline`：写完 `ai_analysis JSON` 后，同事务内把 `PipelineResultDto.knowledgePoints / ideologyMatches` 写入投影表。
- `regenerateTask`：先按 `parse_task_id` 删旧投影再插新。
- 需要新增 `ParseTaskKnowledgePointMapper` 和 `ParseTaskIdeologyMatchMapper`。
- 前端"教案管理 / 思政溯源"查询从扫 `ai_analysis` 改走投影表 + FULLTEXT。

### 额外 FULLTEXT
```sql
ALTER TABLE teaching_materials ADD FULLTEXT INDEX ft_lecture_notes (lecture_notes);
```

### 存量数据回填
- 遍历 `parse_tasks WHERE ai_analysis IS NOT NULL`，反序列化后批量 INSERT 投影表。
- 可写成 `@PostConstruct` 一次性 runner 或独立脚本。

### 估时：2-3 天

---

## Phase 3A：Qdrant 向量检索层

### 目标
为知识点、思政匹配、选区解释提供语义相似检索，支持"找相似教案""历史相似选区解释"等场景。

### 基础设施
```yaml
# docker-compose.yml 片段
services:
  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - qdrant_storage:/qdrant/storage
volumes:
  qdrant_storage:
```

### 后端接口草案

```java
@Service
public class VectorIndexService {
    // 复用现有 LLM provider 的 embedding 接口
    // 配置项：ai.embedding.model / ai.embedding.dimensions
    
    /** 对单条文本生成 embedding 向量 */
    public float[] embed(String text);
    
    /** 批量 embedding */
    public List<float[]> embedBatch(List<String> texts);
    
    /** 索引一条记录到 Qdrant */
    public void upsert(String collection, String id, float[] vector, Map<String, Object> payload);
    
    /** 语义检索 */
    public List<SemanticHit> search(String collection, String query, int topK, Map<String, Object> filter);
}
```

### API 端点
```
POST /api/semantic/search
{
  "scope": "knowledge_points" | "ideology_matches" | "selection_explain",
  "text": "用户输入的自然语言查询",
  "topK": 5,
  "courseId": 123  // 可选过滤
}
→ { "hits": [{ "id", "score", "title", "snippet", "sourceType", "sourceId" }] }
```

### 索引对象
| Collection | 来源 | 文本字段 |
|---|---|---|
| knowledge_points | `parse_task_knowledge_points` / `knowledge_points` | `point_name + definition + evidence_snippet` |
| ideology_matches | `parse_task_ideology_matches` / `teaching_material_traces` | `ideology_element + match_reason + evidence_snippet` |
| selection_explain | `selection_explain_records` | `selected_text + answer` |

### Embedding 模型
- 复用现有 LLM 提供商的 embedding 接口（配置 `ai.embedding.model`）。
- 若提供商不支持 embedding，回退到 `bge-small-zh` 本地模型（需 Python sidecar 或 ONNX Runtime）。

### 写入时机
- Pipeline 完成后异步写入（`@Async`），失败不影响主流程。
- 定时全量 rebuild job（可选，用于初始化 / 数据修复）。

### 前端展示
- "选区解释历史"面板增加"相似历史"推荐卡片。
- "资源管理"页增加语义搜索框（区别于现有关键词搜索）。

### 风险
- Embedding API 调用频次/成本需要控制（可加缓存：相同文本 hash → 相同向量）。
- Qdrant 为外部依赖，服务不可用时应优雅降级（FULLTEXT 兜底）。

### 估时：3-4 天

---

## 毕设叙事建议
Phase 2 + Phase 3 可合并为论文章节"基于多级检索的智慧教学资源管理子系统"：
- 第一级：关系索引（Phase 2 投影表）
- 第二级：全文检索（MySQL FULLTEXT）
- 第三级：语义检索（Qdrant 向量库）

三级检索的层次递进关系天然适合在论文中展开为架构图 + 实验对比。
