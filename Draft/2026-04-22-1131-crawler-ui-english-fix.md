# Crawler And UI English Fix

## 结论

完成四项缺陷修复：主要前端页面可见文案统一改为英文；爬虫摘要和思政标签处理链路补齐提示词、解析与回退；新增跨站标题与正文指纹去重；资源入库不再写入固定默认思政标签，无法可靠匹配时改为空数组。`mvn -q test` 与 `npm run build` 已通过。

## 改动原因

- 对应 `/Draft/毕设.md` 中知识库抓取、AI 摘要提取、思政标签匹配和前端可用性要求。
- 手动运行发现中英混杂、乱码、重复文章和固定默认标签，已直接影响演示可信度与验收质量。

## 具体改动

- `backend/src/main/java/com/smartedu/service/ResourceCrawlService.java`：补跨站去重、修正 AI 提示词与解析、将默认思政标签回退改为空数组。
- `backend/src/main/java/com/smartedu/service/ResourceService.java`：新增按标题查重入口。
- `backend/src/main/java/com/smartedu/service/KnowledgeIngestionService.java`：入库时清理旧思政匹配，禁用按 `matchReason` 和固定标签兜底。
- `backend/src/test/java/com/smartedu/service/ResourceCrawlServiceTest.java`、`backend/src/test/java/com/smartedu/service/KnowledgeIngestionServiceTest.java`：新增回归测试。
- `views`、`components`、`layouts` 下相关页面及 `services/api.ts`：将主要可见 UI 文案统一改为英文，并修正模板下载的运行时文案。

## 方案取舍

- 采用原链路局部修补，没有改数据库结构，也没有扩展新的调度、审核或抽象层，保持最小改动。
- 思政标签仅保留显式可识别结果，不再猜测性补默认值，避免生成“看起来完整但不可信”的数据。
- 个别历史文件存在编码损坏，本次优先修正实际运行路径；残余死代码坏行未做大范围清理，避免误伤无关逻辑。

## 风险与注意事项

- 旧数据库中已经落库的重复文章和固定默认标签不会自动回填清理。
- 当前只验证了本地单测与构建，未在真实联网环境手动跑通第三方站点抓取和外部 LLM provider。
- 个别源文件仍保留历史编码坏行，但当前运行路径已绕开，不影响本次修复目标。

## 验证情况

- `cd backend; mvn -q test`
- `npm run build`
- 结果：均通过。

## 下一位 agent 的接手提示

- 若继续收口，优先补历史数据库脏数据修复脚本，以及真实 provider 与站点抓取的联调验收。
- 重点文件：`backend/src/main/java/com/smartedu/service/ResourceCrawlService.java`、`backend/src/main/java/com/smartedu/service/KnowledgeIngestionService.java`、`views/ResourceLibrary.tsx`。
