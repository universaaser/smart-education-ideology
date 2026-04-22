# Function Acceptance Checklist

## 结论
本清单是后续功能完善的主验收依据。系统最终目标不是只做可点击 demo，而是围绕 `/Draft/毕设.md` 形成“思政知识库、知识图谱、文档解析、教学内容生成、学生学习支持与预警”的完整闭环。

状态标记说明：`[x]` 已具备基础能力；`[~]` 已有 demo 或框架但未达到正式验收；`[ ]` 尚未实现或缺少闭环。后续每次功能完善必须更新本文件的状态、已改动内容和剩余事项。

## 一、基础平台与角色体系
- [x] 用户登录、注册、JWT 鉴权基础流程。
- [x] 教师、学生、管理员角色字段和基础角色分流。
- [~] 基于角色的菜单与视图权限控制。仍需补接口级权限验证和越权测试。
- [ ] 管理员用户管理、课程归属管理、学生-课程绑定关系。
- [~] 头像上传和用户基础资料。仍需补文件安全校验、默认头像策略和失败提示。

## 二、思政知识库与来源追溯
- [x] 资源表、知识点表、来源追溯表等基础数据结构。
- [~] 从思政类网站爬取文章并入库。已补跨站标题与正文指纹去重、摘要回退摘录；站点规则、调度和审计仍主要是 demo 级能力。
- [~] 每条知识点附带来源链接。跨站重复文章已在抓取阶段拦截；仍需补来源可信度、摘录位置、去重审计。
- [ ] 定期自动更新知识库，包括定时任务、失败重试、更新日志、人工审核。
- [ ] 资讯源配置管理，支持扩展人民网、学习强国、新华网、GDELT、arXiv 等来源，但不锁死具体来源实现。

## 三、专业知识与思政价值挖掘
- [~] 调用大模型提取专业知识摘要和思政标签。已修正爬虫提示词、解析与空标签回退链路，默认不再写入固定思政标签；仍需补真实 provider 联调、质量评估和人工复核。
- [~] 支持 DeepSeek/OpenAI 兼容模型配置。Gemini/Doubao/视觉模型仍未正式接入。
- [ ] 物联网专业术语、课程章节、思政元素之间的可审核匹配机制。
- [ ] AI 结果人工确认、修改、重新生成和版本留痕。
- [ ] 面向不同课程的关键词采集与分析任务，而不是固定演示数据。

## 四、课程知识图谱
- [x] 知识图谱节点、关系查询和前端可视化展示。
- [x] 节点位置拖动与保存。
- [x] Excel 模板下载与批量导入基础能力。
- [~] 节点关系创建。仍需补关系类型校验、重复关系提示、删除/编辑体验。
- [~] 基于图谱的学习路径推荐。当前主要依赖图关系和简单优先级，未结合真实学习画像。
- [ ] 图谱增量更新、导入历史、冲突处理、回滚或撤销机制。

## 五、文档上传与结构化解析
- [x] 上传 PDF、Word、PPT、Excel 等文件的前端入口和任务轮询。
- [~] 后端解析任务状态流转和 AI 分析结果展示。已补“解析→提取→匹配→生成”结构化流水线、重生成接口，并接通教师编辑保存链路与 Markdown 导出；仍需 DOCX 导出与更细粒度人工校正。
- [~] 文档内容读取与结构化分析。当前采用配置化 LLM + 固定 JSON 约束，不是正式 MinerU API 解析，复杂 PDF、图片、表格、公式无法保证。
- [ ] 真实 MinerU 或等价解析服务接入，返回章节、段落、表格、图片、知识点等结构化结果。
- [ ] 解析结果人工校正、保存、重新解析、失败重试。
- [x] 上传文件与知识点、课程、生成内容之间的可追溯关系。已实现上传阶段 `courseId` 绑定（`parse_tasks`/`teaching_materials`），新增 `teaching_material_traces` 结构化追溯表，支持任务/版本维度检索与前端筛选展示。

## 六、选段解释与知识点说明
- [x] 后端已有选段解释接口形态。已扩展为结构化请求/响应，支持课程、材料、解析任务上下文。
- [x] 前端教材/文档阅读场景中支持选择文本并发起解释。当前已接入教师材料页 Lecture Notes 选段解释。
- [x] 解释结果必须引用知识库来源，区分“知识库证据”和“模型推理”。已返回 evidenceItems、hasReliableEvidence，并在无证据时明确提示。
- [x] 支持教师或学生查看解释历史。当前已支持教师材料页自动保存和查看解释历史；学生端阅读场景暂未接入。
- [x] 支持解释内容复制、收藏或加入课程材料。当前支持复制和追加到当前讲义草稿；独立收藏库未做。

## 七、智能教学内容生成
- [~] 根据上传文档或课程章节生成教学讲义。已接教师侧讲义编辑与草稿/版本保存，并支持 Markdown 导出；DOCX 与模板化排版仍待补齐。
- [~] 生成贴合物联网专业的案例内容。已支持案例列表可编辑与保存，未补课程级质量规则与人工审核。
- [~] 生成思政融入材料，明确关联的思政元素和引用来源。已展示知识点-思政元素追溯摘要，证据可信度与来源审计仍需加强。
- [~] 生成考核题目、参考答案、评分要点。已接教师侧结构化题目编辑与版本保存，仍需更完整题型约束。
- [~] 教师可编辑、保存、重新生成、导出教学内容。已实现编辑、草稿保存、版本提交、版本回看、显式版本回退为草稿与重生成；已支持 Markdown 导出，DOCX 未完成。
- [x] 生成内容与原始文档、知识点、思政知识库建立追溯关系。已同时保存 `trace_json` 与结构化追溯行，支持按任务/版本/课程/知识点/思政元素检索与分页。

## 八、AI 助手与多模型能力
- [x] AI 对话页面和会话保存基础能力。
- [~] 前端 AI 运行时入口与展示。按用户当前要求，AI 助手已收口为固定本地 OpenAI 兼容运行时 `http://localhost:8317/v1` / `gpt-5.4`，不再暴露失真的 DeepSeek/Gemini 切换；如需恢复正式多模型能力，仍需独立配置页与真实 provider 联调。
- [~] 后端 AI 路由已统一到本地 OpenAI 兼容接口 `http://localhost:8317/v1` 和 `gpt-5.4`，聊天、选段解释、资源抓取 AI 摘要、上传解析流水线、教学内容生成与学习路径建议均复用同一入口；因本地兼容接口 `/responses` 与非流式 `/chat/completions` 仍返回空正文，当前已切为跳过 `/responses` 并直接使用流式 `chat.completions`。
- [~] RAG 增强回答。已具备 chunk 级检索、结构化引用展示和 FOUND/WEAK_MATCH/NO_CONTEXT 提示；仍未实现向量检索、重排序和 RAG 自动评估。
- [ ] 按业务场景区分模型用途，例如聊天、文档解析、视觉分析、题目生成。
- [ ] 模型配置页面或至少完善环境变量模板和部署说明。

## 九、学生学习支持
- [x] 学生端独立首页和知识图谱入口。
- [~] 学习路径推荐展示。仍需结合课程进度、掌握度、错题、兴趣标签。
- [ ] 学习行为采集，包括学习时长、停留时间、答题正确率、路径变化。
- [ ] 学生学习记录、历史报告、推荐原因说明。
- [ ] 个性化复习建议和学习资源推荐。
- [ ] 学生端与 AI 助手、知识图谱、课程资源之间形成闭环。

## 十、学生监测与预警
- [~] 后端已有按专注度、正确率、学习时长、情绪字段计算预警等级的服务。
- [ ] 前端学习行为上报接口和页面埋点。
- [ ] 摄像头授权、视频流采集、关键帧抽取和关闭机制。
- [ ] 视觉模型或等价服务分析专注度、情绪、疲劳状态。
- [ ] 教师端预警列表、等级分布、学生详情、处理状态。
- [ ] 学生端反馈建议、心理调节提示、复习推荐。
- [ ] 隐私合规说明、授权提示和演示模式说明。

## 十一、课程资源库与教师工作台
- [x] 课程资源列表、搜索、分类展示基础能力。
- [~] 教师 Dashboard 统计卡片、趋势图和活动列表。仍需确认统计是否来自真实数据。
- [~] 课程资源与具体课程、章节、知识点绑定。已实现课程资源库按课程查看已保存教学材料与版本分组；章节、知识点级绑定仍未完成。
- [~] 教师可查看资源解析状态、生成内容状态、图谱同步状态。当前已可查看课程下教学材料最新版本状态、历史版本与只读预览；解析状态聚合与图谱同步状态仍未接入。
- [ ] 资源审核、下架、删除、重新同步流程。

## 十二、数据、配置与部署
- [~] MySQL schema 和示例数据存在。仍需区分正式 schema、迁移脚本、演示 seed 数据。
- [~] 前后端构建均可通过。已通过懒加载与基础拆包消除前端 500KB chunk 告警；配置模板与部署说明仍未补齐。
- [ ] `.env.example` 或配置模板，移除默认真实或疑似真实 key。
- [ ] README 补齐本地启动、数据库初始化、环境变量、演示账号、常见问题。
- [ ] 文件上传目录、日志目录、跨域、代理、HTTPS 等部署说明。
- [ ] 生产环境 token、文件、接口权限安全策略。

## 十三、测试与验收
- [~] 后端单元测试和核心 service 测试。已覆盖上传结果接口、教师材料服务关键分支、聊天/鉴权/路径推荐/课程创建/知识图谱坐标更新/资源抓取兼容入口等控制器回归，并新增资源抓取去重/空标签回退、知识入库匹配清理、OpenAI 兼容响应提取与路由策略等 service 回归；仍需扩展集成场景与异常路径。
- [ ] API 集成测试，覆盖认证、上传、图谱、资源、聊天、预警。
- [ ] 前端关键页面 smoke test 或手动验收脚本。
- [ ] 爬虫失败、AI 失败、上传失败、数据库不可用等异常场景测试。
- [ ] 毕设演示脚本，包括教师端主线、学生端主线、知识库更新主线。

## 后续维护要求
- 每次功能完善后，必须在对应条目中更新 `[ ]`、`[~]`、`[x]` 状态。
- 每次功能完善后，必须在“维护记录”中追加本次改动摘要、涉及文件、已完成部分和仍需部分。
- 如果新增功能不属于本清单，需要先补充新验收项，再实现功能。
- 如果发现 `/Draft/毕设.md`、本清单和用户当前要求冲突，以用户当前明确要求优先，并在维护记录中说明取舍原因。

## 维护记录
- `2026-04-22 14:16` | `ai-unified-openai-runtime` | 按用户当前要求将所有 AI 功能统一收口到本地 OpenAI 兼容接口 `http://localhost:8317/v1` / `gpt-5.4`：`AiIntelligenceService` 现将 legacy provider 选择统一映射到 `openai`，并对 localhost 直连跳过空结果 `/responses`、直接使用流式 `/chat/completions`；`AIAssistant` 前端去掉误导性的 DeepSeek/Gemini 切换并展示固定运行时；新增 `AiIntelligenceServiceTest` 回归。关键文件：`backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`、`backend/src/main/resources/application.yml`、`views/AIAssistant.tsx`、`backend/src/test/java/com/smartedu/service/AiIntelligenceServiceTest.java`。冲突说明：本次按用户直接要求暂时弱化“多 provider 切换”demo，以保证聊天、选段解释、上传解析、资源抓取 AI 摘要、教学生成等链路统一走同一接口。仍需：模型配置页、正式多模型能力恢复时的独立联调、连库后的逐页面人工验收。未验证项与风险：未在完整运行态下逐页面手点聊天/上传/抓取/路径推荐链路，当前结论主要基于本地接口实测、后端测试和前端构建。
- `2026-04-22 12:54` | `openai-compatible-chat-fix` | 为 GPT-5/OpenAI 兼容接口补聊天兼容层：`AiIntelligenceService` 现优先尝试 `/responses`，失败后回退 `/chat/completions`，并兼容多种正文提取结构；`AIAssistant` 前端同步展示后端真实错误；新增 `AiIntelligenceServiceTest` 回归。关键文件：`backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`、`views/AIAssistant.tsx`、`backend/src/test/java/com/smartedu/service/AiIntelligenceServiceTest.java`。仍需：继续联调本地兼容服务空正文问题。未验证项与风险：当前本地服务在 `/responses` 与 `/chat/completions` 下仍可能返回空正文，最终是否正常出字仍受上游实现影响。
- `2026-04-22 12:43` | `openai-local-chat-model-gpt54` | 按用户要求将本地 OpenAI 兼容接口默认聊天模型从 `gpt-5.2` 调整为 `gpt-5.4`。关键文件：`backend/src/main/resources/application.yml`。仍需：联调本地兼容服务返回格式，确认 `choices[0].message.content` 不再为空。未验证项与风险：手动探测虽确认接口接受 `gpt-5.4` 请求，但返回体中的 `message.content` 仍为 `null`，AI 助手是否能正常出字仍取决于本地服务兼容性。
- `2026-04-22 12:42` | `openai-local-chat-config` | 将默认聊天配置切到用户提供的本地 OpenAI 兼容接口：`default-chat-provider` 改为 `openai`，后台回退顺序改为 `openai,deepseek,proxy`，并将 `OPENAI_ENABLED/OPENAI_API_KEY/OPENAI_API_BASE/OPENAI_MODEL` 默认值切到 `true`、`your-api-key-1`、`http://localhost:8317/v1`、`gpt-5.2`。关键文件：`backend/src/main/resources/application.yml`。仍需：联调本地兼容服务返回格式，确认 `choices[0].message.content` 不再为空。未验证项与风险：当前手动探测虽接受 `gpt-5.2` 请求，但返回体中的 `message.content` 为 `null`，AI 助手是否能正常出字仍取决于本地服务兼容性。
- `2026-04-22 11:31` | `crawler-ui-english-fix` | 修复资源抓取链路的跨站重复、摘要/思政标签处理和固定默认标签问题，并将主要前端页面可见文案统一为英文，避免中英混杂和乱码。关键文件：`backend/src/main/java/com/smartedu/service/ResourceCrawlService.java`、`backend/src/main/java/com/smartedu/service/ResourceService.java`、`backend/src/main/java/com/smartedu/service/KnowledgeIngestionService.java`、`backend/src/test/java/com/smartedu/service/ResourceCrawlServiceTest.java`、`backend/src/test/java/com/smartedu/service/KnowledgeIngestionServiceTest.java`、`views/AIAssistant.tsx`、`views/Auth.tsx`、`views/Dashboard.tsx`、`views/KnowledgeGraph.tsx`、`views/ResourceLibrary.tsx`、`views/StudentHome.tsx`、`components/Header.tsx`、`layouts/TeacherShell.tsx`、`layouts/StudentShell.tsx`、`services/api.ts`。仍需：历史数据库脏数据修复、真实外部 LLM provider 联调、残余编码坏行安全清理。未验证项与风险：未在真实联网环境手动跑通第三方站点抓取和外部 LLM provider，旧库中的重复文章与默认标签未自动回填。
- `2026-04-16 13:37` | `acceptance-checklist-init` | 初始化系统预期功能验收清单，覆盖基础平台、知识库、知识图谱、文档解析、教学内容生成、AI 助手、学生学习支持、预警、部署和测试。已改动：新增本文件；更新 `AGENTS.md` 维护规则。仍需：后续功能开发时逐项更新状态并补充证据。
- `2026-04-16 18:27` | `upload-pipeline-structured-backend` | 完成上传解析主链路后端强化：新增四阶段结构化流水线（解析/提取/匹配/生成）、统一 JSON 校验与一次重试降级、`result-detail` 与 `regenerate` 接口；入库改为读取结构化结果。关键文件：`backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`、`backend/src/main/java/com/smartedu/service/KnowledgeIngestionService.java`、`backend/src/main/java/com/smartedu/controller/UploadController.java`。仍需：教师端编辑保存导出、解析结果人工校正、MinerU 或等价服务接入。未验证项与风险：真实 LLM 语义质量、复杂文档版式解析、高并发任务稳定性。
- `2026-04-16 19:05` | `teacher-edit-save-trace-closure` | 完成教师侧“编辑+保存+追溯”后端闭环与上传页最小前端接入：新增 `teaching_materials` 持久化模型、`editor-draft/materials/materials/{id}` 接口、草稿保存与正式版本递增、追溯 `trace_json` 构建与展示。关键文件：`backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`、`backend/src/main/java/com/smartedu/controller/UploadController.java`、`backend/src/main/java/com/smartedu/controller/TeachingMaterialController.java`、`views/ResourceUpload.tsx`、`services/api.ts`。仍需：导出、课程级追溯关系建模、追溯检索能力。未验证项与风险：数据库迁移脚本需在目标环境执行；追溯当前为 JSON 快照，复杂查询性能有限。
- `2026-04-16 20:36` | `material-version-history-and-markdown-export` | 完成“导出 + 版本闭环”本轮范围：新增按任务版本列表接口、材料 Markdown 下载接口、上传页版本历史回看与导出按钮；保持旧上传与解析接口兼容。关键文件：`backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`、`backend/src/main/java/com/smartedu/controller/UploadController.java`、`backend/src/main/java/com/smartedu/controller/TeachingMaterialController.java`、`services/api.ts`、`views/ResourceUpload.tsx`。仍需：DOCX 导出、追溯检索拆表。未验证项与风险：导出文件名当前采用服务端安全化规则，未包含中文原始标题；并发编辑冲突策略仍未引入锁或乐观版本控制。
- `2026-04-16 21:10` | `course-trace-rollback-closure` | 完成教师链路剩余闭环：上传接口支持可选 `courseId` 并写入任务与材料版本，AI 流水线注入课程知识上下文；新增 `teaching_material_traces` 独立表和任务/版本追溯检索接口；上传页补齐追溯筛选与“历史版本回退为草稿”显式动作。关键文件：`backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`、`backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`、`backend/src/main/java/com/smartedu/controller/UploadController.java`、`backend/src/main/java/com/smartedu/controller/TeachingMaterialController.java`、`views/ResourceUpload.tsx`、`services/api.ts`、`backend/src/main/resources/migration_material_trace_and_course_binding.sql`。仍需：DOCX 导出、追溯高级检索分析、并发编辑冲突治理。未验证项与风险：迁移脚本需在目标环境执行；旧历史数据依赖懒同步补齐追溯行。
- `2026-04-17 15:21` | `selection-explain-closure` | 完成教师材料页选段解释闭环：扩展 `/api/chat/explain-selection` 结构化请求/响应，新增解释历史表与分页查询接口，前端支持选中文本解释、来源证据展示、历史查看、复制和追加到讲义草稿。关键文件：`backend/src/main/java/com/smartedu/service/ChatService.java`、`backend/src/main/java/com/smartedu/controller/ChatController.java`、`views/ResourceUpload.tsx`、`services/api.ts`、`backend/src/main/resources/migration_selection_explain_records.sql`。仍需：学生端阅读场景接入、独立收藏库。未验证项与风险：迁移脚本需在目标环境执行；真实模型输出质量取决于 provider 配置和知识库命中质量。
- `2026-04-17 15:30` | `selection-explain-review` | 审查最新选段解释改动并修复教师材料页 Lecture Notes 选区读取问题：新增文本框选区状态，避免 `TextArea` 内选中文字后无法发起解释。关键文件：`views/ResourceUpload.tsx`。仍需：浏览器手动点击验收。已验证：`npm run build`、`mvn -q test` 通过；前端仍有既有 chunk 体积警告。
- `2026-04-19 20:54` | `light-rag-improvement` | 完成轻量 RAG 改进：新增 `knowledge_chunks` 检索片段表、FULLTEXT/LIKE 混合召回、聊天接口结构化 citations 和前端引用展示。关键文件：`backend/src/main/java/com/smartedu/service/KnowledgeRetrievalService.java`、`backend/src/main/java/com/smartedu/service/KnowledgeChunkService.java`、`backend/src/main/resources/migration_light_rag_chunks.sql`、`services/api.ts`、`views/AIAssistant.tsx`。仍需：目标环境执行迁移脚本；向量检索、reranker、自动评估未实现。已验证：`mvn -q test`、`npm run build` 通过；未做浏览器手动点击验收。
- `2026-04-21 18:05` | `course-material-library-closure` | 完成教师侧课程资源库挂接教学材料闭环：新增 `/api/courses/{id}/materials` 课程材料分组接口，课程资源库支持按解析任务查看版本历史、只读预览、Markdown 导出与跳转继续编辑，资源上传页新增按 `taskId/materialId` 打开既有任务能力。关键文件：`backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`、`backend/src/main/java/com/smartedu/controller/CourseController.java`、`backend/src/main/java/com/smartedu/service/CourseService.java`、`backend/src/test/java/com/smartedu/service/TeachingMaterialServiceTest.java`、`backend/src/test/java/com/smartedu/controller/CourseControllerTest.java`、`services/api.ts`、`views/ResourceLibrary.tsx`、`views/ResourceUpload.tsx`、`layouts/TeacherShell.tsx`、`types.ts`。仍需：浏览器手动串联“课程页查看/导出/继续编辑”路径，课程章节与知识点粒度绑定、解析状态聚合与图谱同步状态仍未补齐。已验证：`mvn -q test`、`npm run build` 通过；前端仍有既有 chunk 体积警告。
- `2026-04-21 18:24` | `review-quality-optimization` | 完成严格代码评审后的第一阶段质量优化：针对 `TeachingMaterialService` 消除课程材料分组与 trace 构建中的 N+1 查询，并补充批量映射回归测试。本次未调整验收状态，仅提升性能与可维护性。关键文件：`backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`、`backend/src/test/java/com/smartedu/service/TeachingMaterialServiceTest.java`、`Draft/2026-04-21-1824-review-quality-optimization.md`。仍需：继续审查 `views/ResourceUpload.tsx`、`services/api.ts` 的重复流程与默认用户兜底，排查文档/界面文本编码异常。已验证：`mvn -q test` 通过；未验证：浏览器手动回归、前端构建。
- `2026-04-21 18:56` | `review-hardening-round2` | 完成第二阶段评审收口：去掉课程创建、上传、学习路径推荐、聊天选段解释等关键链路中的默认 demo 用户/教师兜底；登录注册与学习路径入口改为 DTO 请求体；前端 API 与页面同步改成显式传递真实用户 ID，并补充 Auth/Path/Upload/Course 回归测试。关键文件：`backend/src/main/java/com/smartedu/controller/AuthController.java`、`backend/src/main/java/com/smartedu/controller/CourseController.java`、`backend/src/main/java/com/smartedu/controller/PathRecommendController.java`、`backend/src/main/java/com/smartedu/controller/UploadController.java`、`backend/src/main/java/com/smartedu/service/ChatService.java`、`services/api.ts`、`views/ResourceLibrary.tsx`、`views/ResourceUpload.tsx`、`backend/src/test/java/com/smartedu/controller/AuthControllerTest.java`、`backend/src/test/java/com/smartedu/controller/PathRecommendControllerTest.java`。仍需：继续处理前端大组件拆分、chunk 体积警告、历史编码异常与剩余弱类型返回结构。已验证：`mvn -q test`、`npm run build` 通过；未做浏览器手动回归。
- `2026-04-21 21:13` | `review-round3-lazy-dto` | 完成第三阶段评审收口：聊天发送、知识图谱坐标更新、资源抓取兼容入口全部改为 DTO 请求体；新增 `KnowledgeControllerTest`、`ResourceControllerTest`；继续拆分 `ResourceUpload` 为独立组件并引入路由/视图级懒加载，前端 500KB chunk 告警已消除；启动类改为标准日志输出。关键文件：`backend/src/main/java/com/smartedu/controller/ChatController.java`、`backend/src/main/java/com/smartedu/controller/KnowledgeController.java`、`backend/src/main/java/com/smartedu/controller/ResourceController.java`、`backend/src/main/java/com/smartedu/dto/ChatMessageRequestDto.java`、`backend/src/main/java/com/smartedu/dto/NodePositionUpdateRequestDto.java`、`backend/src/main/java/com/smartedu/dto/ResourceManualCrawlRequestDto.java`、`backend/src/test/java/com/smartedu/controller/KnowledgeControllerTest.java`、`backend/src/test/java/com/smartedu/controller/ResourceControllerTest.java`、`components/SelectionExplainPanel.tsx`、`components/TeachingMaterialEditorCard.tsx`、`views/ResourceUpload.tsx`、`App.tsx`、`layouts/TeacherShell.tsx`、`layouts/StudentShell.tsx`、`vite.config.ts`、`backend/src/main/java/com/smartedu/SmartEducationApplication.java`。仍需：继续压缩 `ResourceUpload`/`ResourceLibrary` 职责，评估 `AiIntelligenceService` 与 `TeachingMaterialService` 的进一步拆分；浏览器手动回归与集成测试仍缺失。已验证：`mvn -q test`、`npm run build` 通过；未验证：浏览器手动点击链路、生产环境 CORS 与部署安全配置。
