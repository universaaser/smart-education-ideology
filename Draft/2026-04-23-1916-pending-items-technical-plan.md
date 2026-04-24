# Pending Items Technical Plan

## 结论
本文件把 `Draft/2026-04-16-1337-acceptance-checklist.md` 中所有 `[~]` 与 `[ ]` 条目拆成可执行方向，给出最小落地方案：数据结构、接口契约、前端改造点、验证方式。每项都遵循 AGENTS.md 第 0/1/3/4 节（Think Before Coding、Simplicity First、Surgical Changes、Goal-Driven）。后续 agent 可按“优先级 → 分项方案”顺序挑单实施，不必一次性全做。

## 执行优先级（按"毕设答辩演示 + 论文写作"双视角排序）

> 说明：优先级不再按"工程化完备度"排，而是按对**功能闭环、论文得分点、答辩现场可演示性**的直接贡献排。十二、十三章按子项拆分，不再整章分级。

1. **P0 闭环必需（功能主线）**：
   - 五·解析 MinerU
   - 八·RAG/模型分用途
   - 七·教学内容质量（课程规则、题型约束、思政证据）
   - 十三·**演示脚本（13.5）暂缓**：功能尚未全部落地，现在设计脚本会脱离真实链路、反复返工；等 P0/P1 主线基本闭环后再一次性写，避免空转。
2. **P1 主线增量（论文核心章节支撑）**：
   - 二·知识库调度与来源审核
   - 三·匹配审核与版本留痕
   - 四·关系治理与学习路径真实化
   - 九·学生行为采集与复习推荐（闭环描述必备）
   - 十·预警与视觉（毕设题目核心之一，不可省）
   - 十一·课程-章节-知识点绑定、状态聚合
   - 十二·**README + 启动说明（12.4 部分）**（复现门槛）
   - 十三·**API 集成测试 + 异常场景（13.3/13.4）**（论文"系统测试"章需要）
3. **P2 体验与完整度**：
   - 一·管理员控制台、接口级权限
   - 十二·Schema 分层（12.1）
   - 十三·集成测试骨架（13.1）
   - 头像上传加固
4. **P3 工程收尾（对论文/演示几乎无帮助，可最后做或跳过）**：
   - 十二·`.env.example`、HTTPS、生产安全（12.3/12.5/12.6）
   - 十三·Playwright 自动化（13.2）

### 为什么十二、十三不再整体 P0
- **十二**：论文"实现—部署环境"一般一句话带过，`.env.example`、生产安全、HTTPS 不是得分项；但 README + 本地启动说明仍是复现门槛，保持 P1。
- **十三**：论文"系统测试"章通常必写，需要的是**功能测试用例表 + 关键接口截图 + 异常场景说明**，不是完整 CI；自动化 E2E 对论文和演示均无直接帮助，降 P3。演示脚本（13.5）虽是答辩刚需，但当前功能尚未全部落地，提前写脚本会脱离真实链路，不如等主线闭环后统一产出，故本轮暂缓。

---

## 一、基础平台与角色体系

### 1.1 接口级角色权限（1.3 `[~]`）
- **目标**：所有 `controller/*` 按 `teacher/student/admin` 分级授权，越权返回 403。
- **现状**：`RoleAccessService` 已有但未全局拦截；多数接口直接信任前端传入 `userId`。
- **方案**：
  - 新增 `@RequireRole(roles={"teacher","admin"})` 注解 + `HandlerInterceptor` 读取 JWT 中 `role` 校验。
  - 关键接口强制校验：`/api/upload/**`、`/api/teaching-materials/**`、`/api/knowledge/**` 写操作、`/api/alerts/**`、`/api/users/**`。
  - 保留 `userId` 参数，但服务层一律与 JWT 解出的 `currentUserId` 比对，不一致直接 403。
- **测试**：每类控制器加一条越权用例（学生访问教师接口、教师访问管理员接口）。
- **判据**：全部 `controller` 测试含至少一条 `expect 403`。

### 1.2 管理员控制台（1.4 `[ ]`）
- **目标**：管理员可 CRUD 用户、分配课程归属、维护学生-课程绑定。
- **数据**：复用 `users`、`courses`，新增 `course_students(course_id, student_id, PRIMARY KEY(...))` 表。
- **接口**：`/api/admin/users`（分页/搜索/禁用）、`/api/admin/courses/{id}/teachers`、`/api/admin/courses/{id}/students`。
- **前端**：新增 `views/AdminConsole.tsx`，仅在 `role=admin` 下挂到 `TeacherShell`（或独立 AdminShell）。
- **判据**：管理员登录后能完成“新建用户 → 绑定课程 → 学生登录看到课程”全链路。

### 1.3 头像上传加固（1.5 `[~]`）
- **方案**：复用 `/api/upload` 基础设施，但隔离到 `/api/users/{id}/avatar`：限制 `image/*` MIME + 2MB + magic bytes 校验；无头像时前端用确定性默认图（`user.id` hash → 预置色块）；失败提示走统一 `message.error`。
- **判据**：上传非图片、超限、失败均有明确前端提示，不写入脏文件。

---

## 二、思政知识库与来源追溯

### 2.1 站点规则与调度（2.2 `[~]`、2.4 `[ ]`、2.5 `[ ]`）
- **目标**：把爬虫从 demo 升级为可配置、可调度、可审计的来源管道。
- **数据**：新增 `crawl_source(id, name, base_url, rule_json, enabled, schedule_cron, last_run_at, last_status)`；新增 `crawl_run_log(id, source_id, started_at, finished_at, status, stats_json, error)`。
- **服务**：
  - `ResourceCrawlService` 拆出 `CrawlSourceRegistry` 读取 DB 规则；当前硬编码站点迁为 seed 数据。
  - 用 Spring `@Scheduled` + `TaskScheduler` 按 `schedule_cron` 触发；失败指数退避重试 3 次。
  - 每次运行写 `crawl_run_log`，同时落 `resources.audit_json`（抓取时间、规则版本、摘录位置 `excerpt_offset`）。
- **接口**：`/api/admin/crawl-sources` CRUD、`/api/admin/crawl-runs` 查询、`/api/admin/crawl-sources/{id}/trigger` 手动触发。
- **人工审核**：新增 `resources.review_status in (PENDING, APPROVED, REJECTED)`，未审核默认不出现在学生端；教师/管理员页面批量审核。
- **判据**：定时 cron 能触发，失败重试可见日志；审核拒绝后该资源不再入知识点索引。

### 2.2 来源可信度与摘录位置（2.3 `[~]`）
- **数据**：`knowledge_point_sources` 增加 `confidence decimal(3,2)`、`excerpt_start int`、`excerpt_end int`、`snapshot_hash`。
- **评分**：`confidence = 站点权重(source.trust_weight) * 指纹唯一度 * 抽取置信`，写入时一次性算。
- **前端**：选段解释/知识点详情按 confidence 高到低排序，低于阈值给灰色“未核实”标。
- **判据**：同一知识点多来源时可按置信度稳定排序，低置信来源有视觉降权。

---

## 三、专业知识与思政价值挖掘

### 3.1 真实多 Provider 联调（3.1/3.2 `[~]`）
- **方案**：
  - `AiIntelligenceService` 已统一到本地 OpenAI 兼容，保留此为默认；按 `application.yml` 的 `ai.providers.*.enabled` 动态注册 Gemini、Doubao、DeepSeek、视觉模型。
  - 抽 `LlmClient` 接口（`chat`, `stream`, `embed`, `vision`），每种 provider 一个 impl；`AiIntelligenceService` 调用时按 `LlmRoutePolicy.pick(taskType)` 分发。
- **质量评估**：每次生成落 `ai_generation_log(task_type, provider, model, latency_ms, token_usage, rating_null)`；教师端可在结果面板点“👍/👎”回写 rating。

### 3.2 三元匹配人审机制（3.3/3.4 `[ ]`）
- **目标**：专业术语 ↔ 课程章节 ↔ 思政元素的匹配可审核、可版本化。
- **数据**：新增 `subject_ideology_match_review(match_id, version, status, reviewer_id, diff_json, created_at)`（`subject_ideology_match` 已存在实体）。
- **接口**：`/api/matches/pending`、`/api/matches/{id}/approve|reject|revise`；批准后生成新版本，旧版本保留可回滚。
- **前端**：新增 `views/MatchReview.tsx`，分页、批量审核、diff 展示。
- **判据**：AI 产出的匹配默认 `PENDING`；只有 `APPROVED` 版本进入教学材料生成上下文。

### 3.3 关键词采集任务（3.5 `[ ]`）
- **方案**：新增 `keyword_task(id, course_id, keywords_json, created_by, status, result_json)`，由教师在课程页发起；后台走 `CrawlSourceRegistry` 的子集 + LLM 摘要，结果挂到课程上下文。`status` 机打任务状态机 `PENDING→RUNNING→DONE/FAILED`。
- **判据**：教师输入关键词后能看到抓取片段列表，可直接“加入课程知识库”。

---

## 四、课程知识图谱

### 4.1 关系治理（4.3 `[~]`、4.5 `[ ]`）
- **数据**：`knowledge_relations` 增加 `relation_type enum(PRE, POST, RELATED, CONTAINS, CUSTOM)` 校验；在 service 层检测 `(fromId,toId,relation_type)` 重复。
- **接口**：`DELETE /api/knowledge/relations/{id}`、`PUT /api/knowledge/nodes/{id}`（编辑标题/描述/所属章节）。
- **前端**：`KnowledgeGraph.tsx` 详情抽屉增加“关系列表 + 删除按钮”；新建连线时先弹类型选择框。
- **冲突与撤销**：新增 `knowledge_change_log(id, op_type, before_json, after_json, operator_id, created_at)`；提供 `/api/knowledge/undo/{logId}` 逆操作（仅最近 N 条）。
- **判据**：重复关系返回 409；删除节点/关系可在 5 分钟内撤销。

### 4.2 学习路径真实化（4.4 `[~]`）
- **方案**：`PathRecommendService.recommend(studentId, courseId)` 输入改为 `{掌握度向量, 错题集合, 兴趣标签, 图谱拓扑}`；先做加权 topological + 掌握度补齐；后续再替换为模型打分。掌握度来源先用学生活动表聚合（见 9.2）。
- **判据**：不同掌握度画像下推荐顺序不同，单测覆盖。

---

## 五、文档上传与结构化解析

### 5.1 MinerU / 等价解析接入（5.2/5.3/5.4 `[~]`/`[ ]`）
- **方案**：
  - 新增 `MineruParseClient`（HTTP），配置 `mineru.api-base`、`mineru.api-key`，未配置时降级为现有 `DocumentTextExtractor`。
  - 在 `UploadController` 的解析流水线阶段 1（解析）中调用 MinerU，得到 `sections[]/tables[]/images[]/formulas[]`；落 `parse_tasks.structured_result_json`。
  - 后续阶段 2/3/4 消费结构化结果（之前是纯文本，现在按 section 拆分）。
- **降级**：MinerU 失败或未启用时保持原 POI/PDFBox 提取，状态字段标识 `parse_mode=fallback`。
- **判据**：配置开启后上传 PDF，返回章节树、表格、图片列表；关闭后仍能跑完老流程。

---

## 七、智能教学内容生成

### 7.1 课程级质量规则（7.1/7.2 `[~]`）
- **方案**：新增 `course_material_rules(course_id, rule_json)`（最少字数、必须包含章节、案例必须有思政标签）；`TeachingMaterialService.buildPrompt` 注入规则，生成后校验不过则 regenerate 一次后提示教师。
- **判据**：不满足规则的生成结果在保存前被拦截并给出原因。

### 7.2 题型约束（7.4 `[~]`）
- **方案**：题目生成提示词加 `question_type_distribution`（单选/多选/简答/案例比例），生成 JSON 用 `AiPipelineJsonValidator` 校验字段完整性（题干、选项、答案、解析、难度、知识点 id）。
- **判据**：缺字段会触发一次重试，最终保存的题目 100% 通过 schema。

### 7.3 思政证据可信度（7.3 `[~]`）
- **方案**：材料 trace 里展开 `ideology_elements[].confidence` 与 `source_audit`（来源 id、摘录位置），前端在“思政融入”段落右侧显示证据条。低置信元素标灰。依赖 2.2 的字段。

---

## 八、AI 助手与多模型能力

### 8.1 按场景分模型（8.5 `[ ]`）
- **配置**：`application.yml` 增加
  ```yaml
  ai:
    routes:
      chat: openai
      parse: openai
      vision: doubao
      question-gen: deepseek
      embedding: openai
  ```
  `AiIntelligenceService` 通过 `LlmRoutePolicy` 读取路由。

### 8.2 模型配置页（8.6 `[ ]`）
- **前端**：`views/ModelSettings.tsx`（仅 admin），表格形式展示 providers，支持启用/禁用、API base/key（密钥仅写入，不回显）、模型名、测试连接按钮。
- **后端**：`/api/admin/ai-providers`；密钥用现有配置加密方案或 `Jasypt` 存 DB。

### 8.3 主链路向量化与 RAG 评估（8.4 `[~]`）
- **方案**：
  - `ChatService.reply` 接收到用户提问后，先 `VectorIndexService.search(query, topK)`；命中片段注入 prompt，未命中走原 FULLTEXT 降级。
  - 引入轻量 reranker（BM25 + embedding 余弦加权）；离线脚本 `scripts/rag_eval.py` 读取 `rag_eval_set` 表跑命中率/引用准确率。
- **判据**：聊天回复 citations 命中率从现状 FULLTEXT 提升到向量；评估脚本输出 metrics。

---

## 九、学生学习支持

### 9.1 行为采集（9.2/9.3 `[ ]`）
- **数据**：新增 `student_activity_event(id, student_id, course_id, event_type, payload_json, occurred_at)`；event_type 覆盖 `page_stay, answer, path_switch, explain_view, material_open`。
- **前端**：`contexts/TrackingContext.tsx` 封装 `track(event_type, payload)`；学生页面关键动作调用；`beforeunload` 批量发送 `navigator.sendBeacon`。
- **后端**：`/api/student/events`（批量），按 100/批合并落库。
- **判据**：学生完成一次学习会话，后端能查询到事件流。

### 9.2 掌握度与复习（9.4/9.5 `[ ]`）
- **方案**：定时聚合 `student_activity_event` → `student_mastery(student_id, knowledge_point_id, mastery_score, updated_at)`；`PathRecommendService` 与复习推荐同时消费。
- **前端**：学生首页新增“今日复习”卡片，基于 mastery < 阈值的知识点。
- **判据**：模拟答题数据后掌握度与推荐同步变化。

### 9.3 学生闭环（9.6 `[ ]`）
- **方案**：学生端新增“知识点详情”页，聚合：来源、图谱邻居、AI 解释历史、推荐下一步；每个入口都复用现有 API，不新增后端。

---

## 十、学生监测与预警

### 10.1 学习行为预警（10.2 `[ ]`）
- **方案**：依赖 9.1 事件流；`AlertService` 新增基于规则 + 模型的专注度推断；阈值命中写 `alerts` 表并通知教师。

### 10.2 视频与情绪分析（10.3/10.4/10.7 `[ ]`）
- **方案**：
  - 学生端 `useCamera` Hook：`getUserMedia` + 授权弹窗 + 可关闭开关；每 N 秒 `canvas.toBlob` 抽关键帧。
  - 关键帧上传 `/api/student/frames`，后端调用 `VisionLlmClient`（Doubao/GPT-4V），返回 `{focus, emotion, fatigue}`。
  - 隐私：帧默认不落盘，只存分析结果；开始前强制展示《隐私与演示模式》弹窗；提供“仅演示模式”跳过摄像头。
- **判据**：在演示模式和授权模式下均能完整跑通；拒绝授权时系统仍可用。

### 10.3 教师端预警面板（10.5 `[ ]`）
- **前端**：`views/AlertConsole.tsx`（教师），列出学生预警等级分布、学生详情、标记“已处理”。后端补 `/api/alerts/**` 的等级聚合接口。

### 10.4 学生反馈（10.6 `[ ]`）
- **方案**：预警触发时在学生端顶部推送“建议：休息 5 分钟 / 复习 X 知识点”，来源于 `AlertService` 输出 + `PathRecommendService`。

---

## 十一、课程资源库与教师工作台

### 11.1 Dashboard 真实数据（11.2 `[~]`）
- **方案**：`DashboardService` 补齐 SQL：`resources_count`、`materials_count`、`alerts_trend_7d`、`recent_activities`（来自 `system_activity` 表）。移除 demo 写死值。

### 11.2 章节/知识点级绑定（11.3 `[~]`）
- **数据**：`teaching_material_traces` 已有 `knowledge_point_id`；新增 `chapter_id` 字段与 `course_chapters(id, course_id, title, order)` 表。
- **前端**：资源库按课程 → 章节 → 材料版本三级展示。

### 11.3 状态聚合（11.4 `[~]`、11.5 `[ ]`）
- **方案**：新增 `/api/courses/{id}/status-summary`：返回 `{parse_tasks: {...}, materials: {...}, graph_sync: {...}}`；资源审核/下架走 `resources.review_status` + `materials.is_archived`；“重新同步到图谱”重跑 `KnowledgeIngestionService`。

---

## 十二、数据、配置与部署

### 12.1 Schema 分层（12.1 `[~]`）
- **方案**：
  - `backend/src/main/resources/schema/` 存正式 DDL；`seed/demo/` 存演示数据；`migration/` 存增量迁移脚本（已有若干 `migration_*.sql` 汇总到此）。
  - 启动默认只执行 DDL + migration；demo seed 由 `--spring.profiles.active=demo` 控制。

### 12.2 配置模板与部署说明（12.3/12.4/12.5/12.6 `[ ]`）
- **新增**：
  - 根目录 `.env.example`（前端）、`backend/.env.example`（后端）：列出所有必填项，示例值全部为 `your-xxx`。
  - `README.md` 扩写：前置依赖、数据库初始化、配置加载顺序、启动命令（PowerShell）、演示账号、常见问题（CORS、端口冲突、AI 空正文）。
  - `docs/deployment.md`：上传目录、日志目录、HTTPS、反向代理、Token 过期、上传大小、CORS 白名单。
- **安全**：生产环境禁用 `demo` profile；JWT secret、DB 密码、AI key 全部由环境变量注入；上传目录开启 MIME 白名单和杀毒钩子位（预留接口）。

---

## 十三、测试与验收

### 13.1 集成测试（13.3/13.4 `[ ]`）
- **方案**：
  - 新增 `backend/src/test/java/.../integration/` 模块，使用 `@SpringBootTest + Testcontainers-MySQL`。
  - 覆盖：登录、上传→解析→材料版本、知识图谱 CRUD、聊天引用、预警升级。
  - 异常用例：爬虫目标 404、LLM 超时、DB 断连、上传文件非法。

### 13.2 前端自动化 smoke（13.2 `[~]`）
- **方案**：引入 Playwright（`@playwright/test`），脚本放 `e2e/`：教师主线（登录→上传→生成→保存版本→导出）、学生主线（登录→路径→提问→查看解释）。

### 13.3 毕设演示脚本（13.5 `[ ]`，**本轮暂缓**）
- **暂缓原因**：当前 MinerU 解析、向量化、模型路由、学生行为采集、预警视觉等主线功能均未全部落地；现在起草演示脚本只能基于推测的最终形态，后续功能一调整就要整篇返工，违反 Simplicity First 与 Surgical Changes。
- **启动时机**：P0/P1 Sprint 1–5 基本跑通后再一次性产出，届时演示账号、演示数据、每步截图都能对齐真实链路。
- **预期产出**：`Draft/demo-script.md`：演示流程、演示账号、演示数据加载命令、每步期望截图；脚本命令适配 PowerShell。
- **本轮不做**：不提前写脚本骨架，不提前造演示数据。

---

## 路线图建议（按新优先级）
- **Sprint 1（P0 功能主线）**：5.1 MinerU 接入 + 8.1 模型路由 + 8.3 主链路向量化。
- **Sprint 2（P0 教学内容）**：7.1/7.2/7.3 课程规则与思政证据。
- **Sprint 3（P1 知识库与图谱）**：2.1 调度与审核 + 3.2 人审匹配 + 4.1 关系治理 + 4.2 学习路径。
- **Sprint 4（P1 学生闭环 + 预警）**：9.1 行为采集 + 9.2 掌握度/复习 + 10.1/10.2 行为与视觉预警 + 10.3 教师面板。
- **Sprint 5（P1 课程资源 + 论文测试章）**：11.1/11.2/11.3 + 13.3/13.4 API 集成与异常场景测试 + 12.4 README/启动说明。
- **Sprint 6（P2/P3 收尾，时间充足时做）**：1.1/1.2 管理员与权限 + 12.1 Schema 分层 + 13.1 集成测试骨架；`.env.example`、HTTPS、Playwright 视情况跳过。
- **演示脚本（13.5）独立触发**：不挂在任何 Sprint；待 Sprint 1–5 主线功能对齐真实链路后，再一次性产出 `Draft/demo-script.md`，避免因功能未定型导致脚本反复返工。

## 共同约束（所有方案必须遵守）
- 严守 AGENTS.md：最小改动、保持现有风格、不顺手重构、不新增未要求配置项。
- 每个子方案必须在开始前更新 `Draft/2026-04-16-1337-acceptance-checklist.md` 状态并在“维护记录”追加条目。
- 不得把 demo/mock 结果标记为 `[x]`；仅当真实链路跑通并有验证证据时才能升级状态。
- 每次完成后提交一份 `Draft/YYYY-MM-DD-HHMM-<short-name>.md` 改动记录。
