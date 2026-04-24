# MinerU ingestion hardening

## 结论

本轮完成了 MinerU 接入后的两项最小收口修复：上传解析任务入库阶段改为优先复用 `aiAnalysis.documentStructure`，避免新链路把 `parsedContent` 改为 Markdown 后丢失结构化概要；同时移除了 `application.yml` 中硬编码的 MinerU 默认 token，避免继续把疑似真实密钥固化到仓库。

## 改动原因

- 对齐 `/Draft/毕设.md` 中“上传文档后进行结构化解析并生成教学内容”的正式能力。
- MinerU 接入后，`parsedContent` 已从旧的 `DocumentStructureDto JSON` 转为 Markdown 原文；`KnowledgeIngestionService` 若仍按旧格式解析，会导致上传文档转资源时拿不到 `overview/chapterOutline`。
- 当前配置文件仍带默认 MinerU token，不符合验收清单中“移除默认真实或疑似真实 key”的方向。

## 具体改动

- 修改 `backend/src/main/java/com/smartedu/service/KnowledgeIngestionService.java`
  - 新增 `resolveDocumentStructure(...)`，优先从 `PipelineResultDto.documentStructure` 取结构化结果。
  - 仅在旧任务缺少 `documentStructure` 时，才回退解析 `parsedContent` JSON。
- 修改 `backend/src/test/java/com/smartedu/service/KnowledgeIngestionServiceTest.java`
  - 新增 MinerU 新格式优先读取与旧 JSON 回退两条回归测试。
- 修改 `backend/src/main/resources/application.yml`
  - 移除硬编码 MinerU 默认 token，改为只接受 `MINERU_API_KEY` 环境变量注入。

## 方案取舍

- 采用最小修复方案，只修知识入库阶段的结构来源解析，不改上传主链路、不改前端结果渲染。
- 没有把“真实 MinerU 接入”直接改成 `[x]`，因为本轮没有做真实外部服务联调与浏览器验收，只修正接入后的兼容缺口和安全口径。

## 风险与注意事项

- 本轮未执行真实 MinerU 远程解析联调，仍不能证明所有复杂 PDF/表格/公式场景都稳定。
- 移除默认 token 后，如本地未配置 `MINERU_API_KEY`，链路会按既有设计回退到本地提取器，这属于预期行为。

## 验证情况

- 已执行：`mvn -f backend/pom.xml -Dtest=KnowledgeIngestionServiceTest test`
- 结果：通过（5 tests, 0 failures）。
- 未验证：浏览器端上传真实文档后的资源入库展示；真实 MinerU 服务联调。

## 下一位 agent 的接手提示

- 若继续补 MinerU 主线，优先看上传历史/结果页对 `parseMode` 的展示是否足够明确，避免教师看不出当前任务是否真正走了 MinerU。
- 关键文件：`backend/src/main/java/com/smartedu/service/KnowledgeIngestionService.java`、`backend/src/main/resources/application.yml`、`backend/src/test/java/com/smartedu/service/KnowledgeIngestionServiceTest.java`。
- 当前已把下一项“解析来源展示”派给 squad worker，待 inspector 审查。