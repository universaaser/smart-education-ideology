# MinerU 全量结构化接入

## 结论
MinerU 返回的 zip 现在被**彻底结构化**：解压后 markdown、图片、表格、公式、大纲、分块、统计全部落入 `MineruStructuredContentDto`，随 `PipelineResultDto` 一起返回。后端下游 LLM 阶段只喂“摘要结构+原文”不再塞巨型 JSON；前端新增 `MineruStructuredView` 组件以 **概览 / 全文 / 图片 / 表格 / 公式 / 分块** 六个 Tab 呈现。MinerU 失败时仍自动回退到本地抽取器与原 Markdown 卡片。

## 后端变更

### 新增 DTO
- `com.smartedu.dto.mineru.MineruContentBlockDto`：content_list 单条记录（type/text/textLevel/imageUrl/tableBody/...）
- `com.smartedu.dto.mineru.MineruOutlineNodeDto`：大纲节点（title/level/blockIndex/pageIdx/children）
- `com.smartedu.dto.mineru.MineruStatsDto`：页数 / 标题数 / 段落 / 图 / 表 / 公式 / 列表 / 代码 / 字符数
- `com.smartedu.dto.mineru.MineruStructuredContentDto`：blocks + outline + stats + images/tables/equations 分类索引 + assetBaseUrl

### MineruParseClient 重写（`backend/.../service/MineruParseClient.java`）
- 解压 zip 时提取 `full.md`（缺失则其它 `.md`），解析 `content_list.json` 生成 blocks 与大纲，统计汇总入 stats。
- 把图片 / 表格 / 公式 / 图表图片落到 `mineru.assets-path/{batchId}/...`，URL 形如 `/api/upload/mineru-assets/{batchId}/images/xxx.jpg`。
- 防 zip-slip：解析后的绝对路径必须位于 batch 目录内，否则跳过。
- markdown 缺失时用 blocks 兜底合成，保证前端 markdown Tab 始终可渲染。

### 静态资源 / 配置
- 新增 `config/WebMvcConfig.java` 暴露 MinerU 资源目录为静态资源。
- `application.yml`：新增 `mineru.assets-path` / `mineru.asset-url-prefix`。

### AiIntelligenceService
- `runPipeline` 把 `MineruParseResult.structuredContent` 回填到 `DocumentStructureDto.mineruContent`；大纲为空时再用 MinerU outline 回填 `chapterOutline`。
- regenerate 分支保留原有 `mineruContent`。
- 新增 `writeStructureForPrompt(structure)`：面向 LLM 的轻量摘要，剔除 `rawMarkdown` / 全量 blocks，只保留 title/overview/outline/stats + 前 8 条表格/图片/公式的 caption。`extractKnowledgePoints` / `matchIdeologyElements` / `generateTeachingArtifacts` 全部切过去。
- 新增 `collectOutlineTitles` 用于回填章节列表。

### DocumentStructureDto
- 新增 `mineruContent` 字段（可空）。
- `ParseTaskCorrectionService.validateDocumentStructure` 把 `rawMarkdown` / `parseMode` / `mineruContent` 加入 allowed fields，避免 MinerU 元数据触发严格校验失败。

## 前端变更

### 类型（`services/api.ts`）
- `DocumentStructureInfo` 新增 `mineruContent?`
- 新增 `MineruContentBlockInfo` / `MineruOutlineNodeInfo` / `MineruStatsInfo` / `MineruStructuredContentInfo`

### 新组件 `components/MineruStructuredView.tsx`
- Header 展示文件名 / 解析方式 / 模型版本。
- Tabs：
  - **Overview**：统计卡片网格 + 大纲树（antd `Tree`，标注 H1-H6 与页码）
  - **Full Markdown**：沿用 `MarkdownView`
  - **Images**：图片画廊，支持预览 + caption + footnote
  - **Tables**：逐个卡片展示 MinerU 返回的 HTML 表格（轻量 XSS 清洗），fallback 为渲染图
  - **Equations**：图片 + latex 源码
  - **Blocks**：扁平分块列表（Tag 标注 type/H 级别/页码/subType）

### ResourceUpload.tsx
- `normalizePipelineResult` 透传 `mineruContent`。
- 当 `mineruContent` 存在时使用 `MineruStructuredView`，否则退化到原 Markdown 卡片。

## 验证
- `mvn -q -o test -DskipITs`：全部通过（79 个用例）。
- `tsc` 对新组件与 `services/api.ts` 无报错；仓库既有的 `contexts/AuthContext.tsx: import.meta.env` 属预存错误，与本次改动无关。

## 验收建议
1. 启动后端，上传含图表的 PDF；观察后端 `./uploads/mineru-assets/{batchId}/images/` 有图片落盘，`live-log` 输出 `mineru-parse ok batchId=... pages=N`。
2. 前端完成页看到六 Tab 视图，Overview 有统计卡与可展开的大纲；Images/Tables/Equations 能直接预览 MinerU 抽出的素材。
3. 手动 `MINERU_ENABLED=false` 再跑，应回退 Markdown 卡片并带 `Fallback (local extractor)` 标。
4. Regenerate 已完成任务，mineruContent 沿用不丢失。

## 未做
- `layout.json` / `middle.json` / `model.json` 暂未消费（含 bbox/跨行信息），当前 DTO 仅到 content_list 粒度，按需再扩。
- 表格 HTML 的 XSS 清洗为轻量字符串替换，真正上线前建议接入 DOMPurify。
- `span.pdf` / `layout.pdf` 调试图未暴露。
