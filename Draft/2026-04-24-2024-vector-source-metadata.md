# Vector Source Metadata

## 结论

完成 RAG 向量命中来源元数据补全的最小闭环：新写入的知识点/思政匹配向量 payload 会携带 `source` 与 `source_url`，搜索结果解析后可一路透传到聊天 citation。

## 改动原因

- 上一轮聊天主链路已优先使用向量检索，但 vector citation 的来源和链接为空。
- 该改动对应 `/Draft/毕设.md` 中知识库证据可追溯、聊天/RAG 引用可信的目标。

## 具体改动

- `SemanticHitDto` 增加 `source/sourceUrl` 字段。
- `VectorIndexService.parseSearchHits` 从 Qdrant payload 读取 `source` 与 `source_url`。
- `VectorIndexAsyncService` 写入 pipeline 向量时复用 `ResourceCitationDto`，选择首个带来源或链接的 citation 写入 payload。
- `ChatService` 将向量命中的 `source/sourceUrl` 转成 `ChatCitationDto`。
- 新增 `VectorIndexAsyncServiceTest`，并更新聊天、语义搜索、向量解析回归断言。

## 方案取舍

- 本轮只补新索引 payload 与读取透传，不做历史 Qdrant 数据回填，避免引入迁移/后台任务复杂度。
- 暂不调整 selection explain 入聊天 RAG 的范围，保持上一轮只查知识点和思政匹配的主链路语义。

## 风险与注意事项

- 历史已写入的向量 payload 不会自动拥有来源字段，需要重新索引或后续回填。
- 当前只写入第一条有效 Resource citation，暂不展开多来源列表。
- 向量命中仍缺少 score 阈值、弱匹配状态和重排序。

## 验证情况

- `mvn -f backend/pom.xml -Dtest=ChatServiceTest,ChatControllerTest,VectorIndexServiceTest,VectorIndexAsyncServiceTest,SemanticSearchControllerTest test` 通过。

## 下一位 agent 的接手提示

- 若继续做 P0 RAG，优先考虑 score 阈值/WEAK_MATCH 或一次 embedding 复用多 collection。
- 若要让历史向量 citation 也带来源，需要设计重新索引或回填流程。
