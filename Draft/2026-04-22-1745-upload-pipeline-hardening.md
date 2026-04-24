# 上传-解析流水线硬化（P0-P3）

按 `plans/upload-pipeline-goal-plan-640ca4.md` 实施，解决 AI 流水线即便 API 恢复后仍阻碍原有目标的四个深层问题。

## 变更清单

### P1 · 失败不污染图谱

- `@backend/src/main/java/com/smartedu/service/AiIntelligenceService.java` `runPipeline` 末尾根据 `inferred` 分叉：
  - `inferred=false` → 原路径（`ingestParseTask` + `COMPLETED`）。
  - `inferred=true` → 跳过 ingest，任务置 `FAILED`，`currentStep` 标注"AI pipeline fell back to stub; knowledge graph not updated."，`errorMessage` 汇总 warnings（1000 字截断）。
  - `regenerate-retry` 成功路径补一次 ingest + 置 COMPLETED，保证对称。
- 影响：本次日志里那种"全 fallback 仍写 `subject_knowledge.id=21` / `resources.id=32` / 2 条 chunk"的情况不再发生。

### P0 · 真实文本抽取

- 新增 `@backend/src/main/java/com/smartedu/service/DocumentTextExtractor.java`：按扩展名分派
  - `.pdf` → PDFBox `PDFTextStripper`
  - `.doc`/`.docx`/`.ppt`/`.pptx`/`.xls`/`.xlsx` → POI `ExtractorFactory`
  - 其他 → UTF-8 / GBK 纯文本读
  - 单文件失败最终回落到纯文本读或返回 null，不抛出。
- `AiIntelligenceService` 注入 `DocumentTextExtractor`，原 `readFileContent` 内部只做一次委托。
- `pom.xml` 新增 `poi-scratchpad:5.2.5`（覆盖老版 `.doc/.ppt`）与 `pdfbox:3.0.3`；`poi-ooxml` 原有。

### P2 · 熔断半开恢复

- `disabledProviders` 由 `Set<String>` 升级为 `Map<String, Long>`（熔断起始时间戳）。
- 新增 `isCircuitOpen(providerKey)`：冷却期过后自动清除标记并允许一次半开探活；失败则在 `markProviderFailure` 中再次打上时间戳。
- 新增配置 `ai.routing.circuit-cool-down-seconds`（默认 60），可通过环境变量 `AI_CIRCUIT_COOL_DOWN_SECONDS` 覆盖。
- 移除无用的 `import java.util.Set`。

### P3 · 真实 provider 回退链

- 新增配置 `ai.routing.background-chain`（默认 `openai`），支持逗号分隔多节点；环境变量 `AI_BACKGROUND_CHAIN` 覆盖。
- `buildBackgroundProviderChain` 按配置顺序解析，空配置回退到 `default-chat-provider`，再回退到单节点 `openai`。
- `buildPreferredProviderChain(preferred)` 将前端偏好置顶，其后附加 background-chain 中剩余节点，实现真正的自动回退。
- 移除 `resolveUnifiedProviderKey`（原先把 default/proxy/deepseek/gemini 都强制归一为 openai，导致多节点配置失效）；改为 `normalizeProviderAlias` 仅做 `default→proxy` 的别名映射，其余交给 `getProviderSettings` 校验。
- **默认行为未变**：`background-chain=openai`，单节点路径与改动前一致；仅在用户显式配置多节点时才启用回退。

## 验证

- `mvn -q -DskipTests compile` 通过。
- `mvn -q -DskipTests test-compile` 通过。
- 运行时验证（需用户执行）：
  1. 上传 `数字技术赋能高校精准思政研究_王木.pdf`：`parsedContent.overview` 应为真实中文段落而非 `Fallback Document`。
  2. 人工断网模拟 AI 失败：任务变 `FAILED`；`subject_knowledge`/`knowledge_chunks`/`resources` 无新增行。
  3. 熔断后等待 60 秒再次请求：日志出现 `AI provider circuit half-open after cool-down: provider=openai` 并重新发起调用，不需重启后端。
  4. 在 `application.yml` 设 `background-chain=openai,deepseek`、把 `openai.api-key` 置空、`deepseek.enabled=true` 配好 Key：后台任务自动命中 deepseek。

## 不在本次范围

- MinerU 真实接入（验收清单 `[ ]` 项，独立大任务）。
- Prompt 调整（先验证"真实输入 + 真实调用"的基线质量）。
- 前端视图提示（P1 直接 FAILED，前端无需改）。
- 历史污染数据的清理（用户可按需用 SQL 清 `subject_knowledge.id=21` 等；此次不提供批量清理接口）。

## 风险

- PDFBox + POI 引入约 15MB 依赖；启动无额外影响，仅打包体积增加。
- 扫描型 PDF（无文字层）会返回空串导致 inferred=true → 任务 FAILED。这是预期行为，比静默生成垃圾数据更好。
- 若未来用户显式把 `background-chain` 配为多节点且第二个节点 API Key 错误，会在每次回退时多一次调用失败日志；可通过 `failure-threshold` 控制。
