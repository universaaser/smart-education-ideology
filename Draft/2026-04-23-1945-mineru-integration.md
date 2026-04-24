# MinerU 文档解析接入

## 结论
按 checklist 5.1/5.2/5.3/5.4 要求，文档解析链路改为 **MinerU 优先 → 本地抽取回退 → LLM 结构化**。MinerU 成功时其 markdown 既是前端 `Parsed Content Summary` 的展示源，也作为下游知识点 / 思政匹配 / 教学内容生成的原文输入；未配置或调用失败自动降级到原 `DocumentTextExtractor`，整条流水线不中断。

## 变更范围

### 后端
- 新增 `backend/src/main/java/com/smartedu/service/MineruParseClient.java`：
  - 实现 MinerU v4 本地文件批量解析链路：`POST /file-urls/batch` → `PUT` 上传 → 轮询 `/extract-results/batch/{batchId}` → 下载 zip → 抽 `full.md`（失败时用 `content_list.json` 兜底拼 markdown）。
  - 所有异常封装为 `MineruParseException`；`isAvailable()` 判定配置是否完备。
- `dto/DocumentStructureDto` 增加 `rawMarkdown`、`parseMode` 字段，用于留痕与前端展示。
- `service/AiIntelligenceService.runPipeline` 重构：
  - 首次解析：先 `tryMineruParse`，成功则把 markdown 直接写入 `task.parsedContent` 并作为 `rawContent` 喂 LLM 阶段；失败走 `DocumentTextExtractor` 并落 warnings。
  - regenerate：不再把 `parsedContent` 反序列化为 JSON，改为从 `aiAnalysis.PipelineResultDto.documentStructure` 还原结构（新增 `loadExistingStructure`、`extractExistingRawMarkdown` 辅助）。
  - `parsePipelineResult` 对 `parsedContent` 兼容 JSON / markdown 两种历史写法。
- `application.yml`：`mineru.*` 补齐 `enabled/model-version/language/enable-formula/enable-table/is-ocr/http-timeout`，`base-url` 改为 `https://mineru.net/api/v4`，默认 api-key 填入用户提供的 token（生产用 `MINERU_API_KEY` env 覆盖）。

### 前端
- `services/api.ts`：`DocumentStructureInfo` 增加 `rawMarkdown?` 与 `parseMode?`。
- `views/ResourceUpload.tsx`：`normalizePipelineResult` 透传新字段；`Parsed Content Summary` 标题旁加 Tag 标记 `MinerU` / `Fallback`。

### 测试适配
- 5 处 `new AiIntelligenceService(...)` 的构造参数补齐 `MineruParseClient`（`AiIntelligenceServiceTest`、`TeachingMaterialServiceTest`、`ParseTaskCorrectionServiceTest`、`ChatServiceTest`、`UploadControllerTest`、`TeachingMaterialControllerTest`）。

## 验证建议
1. 启动后端，确保 `application.yml` 里 `mineru.api-key` 非空（默认已填）。
2. 前端上传 PDF，观察：
   - 任务 `currentStep` 先出现 `Parsing document with MinerU...`；
   - 完成后 `Parsed Content Summary` 卡片标题带 `MinerU` Tag，渲染的是真实 markdown；
   - `live-log` 里出现 `mineru-parse ok batchId=...`。
3. 临时把 `MINERU_ENABLED=false` 或 key 改为空，重跑：应看到 Tag 变 `Fallback (local extractor)`，流水线仍能完成。
4. 已完成任务点 `Regenerate`：应复用既有 markdown 和结构，不再重新上传。

## 未做 / 待跟进
- MinerU 返回的 `content_list.json` / 图片 / 表格尚未拆出到结构化字段，仍由 LLM 阶段 2 从 markdown 提炼；若后续要把表格/公式单独落库，再扩 `MineruParseResult`。
- 生产环境需要把默认 api-key 从 `application.yml` 删除，统一由 `MINERU_API_KEY` 环境变量注入。
- 集成测试未在本次改动中补充；保留作为 13.3 条目跟进。
