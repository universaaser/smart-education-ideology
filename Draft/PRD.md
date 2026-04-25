# 新增功能推进 PRD（Agent 执行版）

## 结论

本 PRD 面向后续 agent 开发使用，目标不是继续深挖 MinerU、RAG、AI 路由等已有主链路的工程质量，而是在不突破 `/Draft/毕设.md` 范围的前提下，按“可演示、可写论文、能体现工作量”的顺序补新增功能。当前系统已经有教师端、学生端、上传解析、知识图谱、资源库、AI 助手、选段解释、MinerU 结构化展示、向量检索和课程级发布规则等基础，后续更适合横向补页面、流程、状态、记录和审核入口。

建议后续开发按本文顺序推进：先补学生行为与预警闭环，再补课程资源组织、知识源管理、关键词任务、匹配审核、模型配置、管理员控制台，最后补图谱细节、演示脚本和测试文档。每项实现完成后再更新 `Draft/2026-04-16-1337-acceptance-checklist.md`，本 PRD 本身不改变验收状态。

## 改动原因

- 与 `/Draft/毕设.md` 的关系：本文覆盖其四条主线中的思政知识库、知识图谱、智能教学内容生成、学生自主学习监测与预警，属于“补充目标功能实现计划”。
- 与 `acceptance-checklist` 的关系：当前 `[ ]` 和 `[~]` 主要集中在学生行为、预警、知识源调度、匹配审核、管理员、模型配置、课程章节绑定和测试验收；这些都适合通过新增页面和新增流程体现进度。
- 与 `pending-items-technical-plan` 的关系：该计划偏工程落地拆解，本文改为 PRD 视角，强调最佳实现顺序、用户流程、功能验收和 agent 可执行任务边界。
- 与用户当前要求的关系：优先“新增功能”和“可见工作量”，适当降低生产级健壮性、优雅代码和自动化测试要求，但不能伪造已完成能力，也不能突破毕设系统范围。

## 当前系统实际基础

### 前端现状

- 入口为 `App.tsx`，按登录角色进入 `TeacherShell` 或 `StudentShell`。
- 教师端现有视图：`Dashboard`、`KnowledgeGraph`、`AIAssistant`、`ResourceUpload`、`ResourceLibrary`。
- 学生端现有视图：`StudentHome`、`KnowledgeGraph`、`AIAssistant`、`ResourceLibrary`。
- 现有公共能力：`AuthContext`、`services/api.ts`、`components/SelectionExplainPanel.tsx`、`components/MineruStructuredView.tsx`、`components/TeachingMaterialEditorCard.tsx`、`components/TeachingMaterialPreview.tsx`。
- `types.ts` 已有 `Role.ADMIN`、`canManageUsers`、`canViewStudentAlerts`、`canSubmitLearningActivity` 等能力位，适合直接扩展新增菜单。

### 后端现状

- 已有控制器：认证、聊天、课程、仪表盘、知识图谱、路径推荐、资源、语义搜索、教学材料、上传、用户。
- 已有服务：`AlertService`、`PathRecommendService`、`ResourceCrawlService`、`KnowledgeService`、`TeachingMaterialService`、`AiIntelligenceService`、`MineruParseClient`、`VectorIndexService`。
- 数据层已有：`users`、`courses`、`resources`、`knowledge_points`、`knowledge_relations`、`subject_knowledge`、`subject_ideology_matches`、`student_activities`、`parse_tasks`、`teaching_materials`、`teaching_material_traces`、`course_material_rules`、`knowledge_chunks`、`selection_explain_records`。
- 缺口主要不是“没有基础”，而是“没有足够多的可见功能入口、审核流程、状态面板和闭环页面”。

## 产品目标

1. 教师端能展示更多工作流：学生预警、资源审核、关键词采集、匹配审核、课程章节资源组织、模型配置。
2. 学生端形成闭环：学习行为上报、学习报告、复习建议、预警反馈、从复习建议跳转知识图谱或 AI 助手。
3. 管理员角色有独立存在感：用户管理、课程绑定、模型配置、知识源配置。
4. 论文可写更多章节：需求分析、系统设计、数据表、功能模块、测试用例、演示流程都有材料。
5. 每个功能接受“本科毕设 demo 级完成”：能跑通主要路径、能截图、能写测试表；不追求生产级并发、权限矩阵、复杂回滚、全量自动化测试。

## 非目标

- 不做新系统范围：不新增支付、班级排课、正式 LMS、企业级审计、微服务拆分、复杂权限中心。
- 不深挖生产安全：HTTPS、杀毒、密钥托管、全局接口权限矩阵可放到低优先级。
- 不把 demo/mock 标成已完成：如果视觉模型、外部资讯源或多 provider 只是演示模式，只能在验收清单标 `[~]`，不能标 `[x]`。
- 不为优雅重构扩大范围：不要重写 `AiIntelligenceService`、不要拆大组件作为独立任务、不要引入状态管理库。

## 总体实现策略

- 前端优先用现有 Ant Design、现有 `View` 枚举、`TeacherShell`、`StudentShell` 和 `services/api.ts` 扩展。
- 后端优先新增小控制器和小服务，不改现有主链路；数据库用单个迁移 SQL 补表或补字段。
- 每个功能保留“演示模式”或“最小可用默认值”，例如视觉分析可先返回规则化结果，多模型配置可先完成配置展示和连接测试，不要求真实 provider 全部稳定。
- 每项功能至少有一个可演示入口、一个后端接口、一条数据记录、一段验收说明。
- 文案和代码标识符默认用英文；页面已有英文风格，新增页面继续保持英文 UI。

## 状态标记与维护规则

本文每个功能包标题和功能包内的子功能点都使用统一状态位，后续 agent 必须维护：

- `[TODO]`：尚未开始实现。
- `[DOING]`：正在本轮或上一轮实现，但尚未达到本文定义的完成边界。
- `[DONE]`：已经达到本文定义的完成边界；达到后不再在本条目里继续扩 scope。
- `[BLOCKED]`：因外部服务、数据、配置或冲突无法继续，必须写明阻塞原因和可恢复条件。
- `[DEFERRED]`：明确推迟，不计入当前赶进度主线。

禁止在本文使用 `[~]` 作为状态位。本文不是无限细化清单，而是按子功能点判断是否“做到本 PRD 定义的 demo 完成边界”。如果后续想继续打磨健壮性、体验或生产级能力，必须由人工操作新增独立 PRD 条目或变更文档，agent不能把已 `[DONE]` 的子功能点重新拖回半完成。

大功能包标题状态只表示汇总状态：

- 包内所有必做子功能点均为 `[DONE]` 时，大功能包才能改为 `[DONE]`。
- 包内任一必做子功能点为 `[DOING]` 时，大功能包为 `[DOING]`。
- 包内只有可选子功能点未做，不影响大功能包 `[DONE]`，但可选项必须保持 `[TODO]` 或 `[DEFERRED]`，不能写成半完成。
- 后续 agent 必须优先更新子功能点状态，再按规则更新大功能包标题。

后续 agent 只要改动本文任一功能包对应代码，必须同时更新三处：

1. 本 PRD：更新子功能点状态位，必要时同步更新大功能包标题状态，并在“PRD 实现记录”追加实现时间、功能包、子功能点、完成边界命中情况、变更文档。
2. `Draft/2026-04-16-1337-acceptance-checklist.md`：更新对应条目状态，并在维护记录追加本次改动、关键文件、未验证项和风险。
3. 本次变更文档：新增 `Draft/YYYY-MM-DD-HHMM-<short-name>.md`，说明实际完成内容、验证情况和未做范围。

若 checklist 的原条目范围大于本文功能包，允许 checklist 仍保持 `[~]`，但本文功能包只按本文“完成边界”判断 `[DONE]`。不要为了追求 checklist `[x]` 继续扩大当前功能包。

## 最佳实现顺序

### 1. [DONE] 学生学习行为采集与学习报告

**优先级理由**：这是学生监测、预警、学习路径真实化、复习推荐的共同前置能力；新增页面明显，论文可写“学习行为数据采集模块”。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 1.1 行为事件数据结构 | `[DONE]` | 是 | 新增或复用表结构，能保存学生、课程、事件类型、payload、发生时间 | 已新增 `student_activity_events`，支持 `page_stay/material_open/knowledge_view/ai_ask` 等事件并按学生、课程、时间查询 |
| 1.2 批量事件上报接口 | `[DONE]` | 是 | 新增 `POST /api/student/events`，接收前端批量事件 | 已新增接口，合法事件批量入库；空数组、缺少 studentId/courseId 返回明确失败 |
| 1.3 前端 tracking 封装 | `[DONE]` | 是 | 新增 `TrackingContext` 或等价工具，统一封装 `track(eventType,payload)` | 已新增 `TrackingContext`，学生端可直接调用且失败不阻塞主页面 |
| 1.4 关键入口埋点 | `[DONE]` | 是 | 在学生首页、资源打开、图谱节点查看、AI 提问入口埋点 | 已覆盖学生页面停留、课程/材料打开、图谱节点查看、AI 提问四类真实入口 |
| 1.5 学习报告接口 | `[DONE]` | 是 | 新增 `GET /api/student/report` 和最近活动查询 | 已返回今日学习时长、事件数、知识点访问数和近 7 日趋势，并提供最近活动接口 |
| 1.6 学生首页报告卡片 | `[DONE]` | 是 | 在 `StudentHome` 展示学习报告和最近记录 | 已在学生首页展示学习报告、趋势和最近学习记录，空数据有空态 |
| 1.7 答题正确率接入 | `[TODO]` | 否 | 有真实答题入口后记录 `answer_submit` | 能统计正确率即可；无正式答题入口时保持 `[TODO]` 或 `[DEFERRED]` |

**用户故事**

- 学生进入学习空间后，系统记录页面停留、打开材料、查看知识点、发起 AI 提问、答题结果等行为。
- 学生能在首页看到今日学习时长、访问知识点数、答题正确率、最近学习记录。
- 教师后续可基于这些记录查看学生状态。

**功能范围**

- 学生端新增 `Learning Report` 区块，建议放在 `StudentHome.tsx`。
- 新增轻量行为埋点上下文 `contexts/TrackingContext.tsx`，封装 `track(eventType, payload)`。
- 关键事件类型先覆盖：`page_stay`、`material_open`、`knowledge_view`、`ai_ask`、`answer_submit`、`path_switch`。
- 后端新增 `StudentActivityController`，提供批量上报、学习报告、最近记录接口。
- 数据上可以复用 `student_activities` 做聚合记录；如需要展示事件流，新增 `student_activity_events`。

**建议接口**

- `POST /api/student/events`：批量上报事件。
- `GET /api/student/report?studentId=&courseId=`：返回今日统计和近 7 日趋势。
- `GET /api/student/recent-activities?studentId=`：返回最近学习记录。

**验收标准**

- 学生进入首页、打开资源、查看图谱节点、提问 AI 后，后端能查到事件。
- 学生首页展示今日学习数据，不需要百分百精确，能稳定随事件增加而变化即可。
- 页面刷新后学习报告仍能加载。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 后端具备批量事件上报、学习报告、最近活动三个接口。
- 学生端至少在首页进入、资源打开、图谱节点查看、AI 提问四个真实入口调用 `track`。
- `StudentHome` 展示今日学习时长、学习事件数、知识点访问数、近 7 日简要趋势中的至少三项。
- 事件与报告数据刷新后仍可读；不要求高精度停留时长和离线补偿。
- 1.1 至 1.6 全部 `[DONE]`；1.7 是可选项，不影响本功能包完成。

**可放宽项**

- 暂不做高精度停留时长；可以用进入/离开时间差或前端定时心跳估算。
- `beforeunload` 丢事件可以接受，不做复杂离线队列。

### 2. [DONE] 学生预警中心与学生反馈

**优先级理由**：`/Draft/毕设.md` 明确要求学生自主学习监测与预警，当前已有 `AlertService` 但缺页面和闭环。先做行为预警，视觉分析可用演示模式补位。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 2.1 预警记录数据结构 | `[DONE]` | 是 | 新增 `student_alert_records` 或复用 `student_activities` 扩展处理状态 | 能保存学生、课程、等级、原因、建议、状态、创建时间 |
| 2.2 预警生成接口 | `[DONE]` | 是 | 新增基于近期行为的预警刷新接口 | 能从行为或活动数据生成 0-3 级预警；无数据返回正常空结果 |
| 2.3 教师预警汇总接口 | `[DONE]` | 是 | 提供等级分布、未处理数量、最近预警 | 汇总数据来自真实表；至少支持 courseId 过滤 |
| 2.4 教师预警列表与详情 | `[DONE]` | 是 | 新增 `AlertConsole` 页面 | 可分页查看、按等级筛选、打开详情；空数据有空态 |
| 2.5 预警处理状态 | `[DONE]` | 是 | 教师可标记 `PENDING/HANDLED/IGNORED` 或等价状态 | 状态更新后列表立即变化，刷新仍保留 |
| 2.6 学生反馈建议 | `[DONE]` | 是 | 学生首页展示与预警等级对应的建议 | 至少展示休息建议、复习建议或 AI 提问建议之一，并能关闭或跳转 |
| 2.7 视觉演示模式 | `[TODO]` | 否 | 展示 focus/emotion/fatigue 的 demo 结果 | 页面和数据明确标记 `Demo Vision Mode`；无真实模型也可完成 |
| 2.8 真实摄像头关键帧 | `[TODO]` | 否 | getUserMedia、抽帧、上传、视觉模型分析 | 拒绝授权不影响系统；真实模型可用前不要求完成 |

**用户故事**

- 教师能看到学生预警列表、等级分布、触发原因和处理状态。
- 学生在学习状态异常时能看到系统建议，例如休息、复习某个知识点、向 AI 助手提问。
- 管理员或教师能演示“行为采集 -> 预警生成 -> 教师处理 -> 学生收到反馈”的闭环。

**功能范围**

- 新增 `views/AlertConsole.tsx`，挂到教师端菜单。
- `StudentHome.tsx` 顶部新增预警反馈条或建议卡片。
- 后端新增 `AlertController`，复用 `AlertService` 的等级计算。
- 数据可以先基于 `student_activities.alert_level/alert_message` 展示；如需要处理状态，新增 `student_alert_records`。
- 视觉分析先做“演示模式”：不强制调用摄像头；提供开关展示 `focus/emotion/fatigue` 示例结果，后续再接真实关键帧上传。

**建议接口**

- `GET /api/alerts/summary?courseId=`：等级分布、未处理数量。
- `GET /api/alerts?courseId=&level=&status=`：分页列表。
- `POST /api/alerts/evaluate?studentId=&courseId=`：基于近期行为生成或刷新预警。
- `PUT /api/alerts/{id}/status`：标记已处理、忽略或跟进中。
- `GET /api/student/feedback?studentId=&courseId=`：学生端建议。

**验收标准**

- 通过模拟或真实学习事件能生成轻/中/重预警。
- 教师端列表可筛选、可查看详情、可标记已处理。
- 学生端能看到与预警等级对应的建议。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 教师端出现 `Alerts` 页面，并能展示预警汇总、分页列表、详情和处理状态。
- 后端能基于近期学生行为或 `student_activities` 生成预警记录。
- 学生端首页能展示至少一条与预警等级对应的反馈建议。
- 视觉分析如果只做演示模式，页面和返回数据必须明确标记 `Demo Vision Mode`；不要求真实摄像头和真实视觉模型。
- 2.1 至 2.6 全部 `[DONE]`；2.7、2.8 是可选项，不影响本功能包完成。

**可放宽项**

- 暂不强制真实摄像头；视觉预警可显示“Demo Vision Mode”。
- 暂不做 WebSocket 推送，学生刷新或轮询即可。

### 3. [DONE] 课程章节、知识点绑定与资源状态汇总

**优先级理由**：现有资源库和上传解析已经较完整，但“课程 -> 章节 -> 知识点 -> 材料版本”的组织还不够明显。该功能改动适中，能显著提升教师端完整度。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 3.1 课程章节数据结构 | `[DONE]` | 是 | 新增 `course_chapters`，含 courseId、title、sortOrder、status | 已新增 `course_chapters`，支持按课程查询并按 sortOrder/id 排序；未引入 status，当前以新增/编辑闭环为边界 |
| 3.2 章节 CRUD 接口 | `[DONE]` | 是 | 新增章节新增、编辑、列表接口 | 已支持列表、新增、编辑；删除/禁用不属于本轮必需边界 |
| 3.3 材料章节绑定 | `[DONE]` | 是 | 新增材料版本与章节绑定关系 | 已在 `teaching_materials.chapter_id` 保存版本章节归属，并提供材料绑定章节接口 |
| 3.4 资源库章节树展示 | `[DONE]` | 是 | `ResourceLibrary` 按课程 -> 章节 -> 材料版本展示 | 已按课程章节分组展示材料版本；未绑定材料进入 `Unassigned Materials` 分组 |
| 3.5 上传/编辑页选择章节 | `[DONE]` | 是 | 保存材料草稿或发布版本时可选择章节 | 已在上传/编辑页按课程加载章节，保存草稿和发布版本时带章节归属 |
| 3.6 课程状态汇总接口 | `[DONE]` | 是 | 新增 `/api/courses/{id}/status-summary` | 已返回章节数、解析任务数、材料版本数、草稿数、发布数、知识点数 |
| 3.7 章节知识点绑定 | `[TODO]` | 否 | 章节直接绑定知识点 | 可在章节下展示知识点列表；没有该项不影响章节材料闭环 |

**用户故事**

- 教师能为课程创建章节，把教学材料和知识点绑定到章节。
- 教师能在资源库按课程、章节、材料版本查看资源。
- 教师能看到某课程的解析状态、材料生成状态、图谱同步状态。

**功能范围**

- 新增 `course_chapters` 表。
- `teaching_material_traces` 增加 `chapter_id`，或者新增 `chapter_material_bindings` 避免影响旧 trace。
- `ResourceLibrary.tsx` 改为课程列表 + 章节树 + 材料版本列表。
- `CourseController` 新增章节 CRUD 和课程状态汇总接口。
- 上传页保存材料时允许选择章节。

**建议接口**

- `GET /api/courses/{courseId}/chapters`
- `POST /api/courses/{courseId}/chapters`
- `PUT /api/courses/{courseId}/chapters/{chapterId}`
- `POST /api/courses/{courseId}/chapters/{chapterId}/materials/{materialId}`
- `GET /api/courses/{courseId}/status-summary`

**验收标准**

- 教师能新建章节，并把一个已生成材料绑定到章节。
- 资源库能按章节分组展示材料版本。
- 状态汇总至少展示：解析任务数量、已发布材料数量、图谱节点数量或同步状态。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 支持课程章节新增、编辑、删除或禁用中的至少新增和编辑。
- 支持把已有教学材料版本绑定到章节，并能在资源库按课程 -> 章节 -> 材料版本展示。
- 课程状态汇总接口返回解析任务、材料版本、图谱同步或节点统计中的至少三类数据。
- 不要求章节拖拽排序、复杂同步 diff 或历史版本冲突治理。
- 3.1 至 3.6 全部 `[DONE]`；3.7 是可选项，不影响本功能包完成。

**可放宽项**

- 章节排序可用简单 `sort_order`，不做拖拽。
- 图谱同步状态可先用“最近同步时间 + 节点数量”表示，不做复杂 diff。

### 4. [DONE] 知识源配置、抓取日志与人工审核

**优先级理由**：思政知识库是系统第一条主线。当前已有爬虫入口和资源表，但看起来偏 demo；新增知识源配置和审核页面能显著增加功能厚度。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 4.1 知识源配置表 | `[DONE]` | 是 | 新增 `crawl_sources`，保存名称、baseUrl、enabled、备注 | 已新增 `crawl_sources`，服务和 Source Management 页面可读取来源配置；站点规则仍复用后端硬编码 |
| 4.2 知识源管理接口 | `[DONE]` | 是 | 来源列表、新增、编辑、启用停用 | 已支持来源列表、新增、编辑和启用状态保存，刷新后可保留 |
| 4.3 抓取运行日志 | `[DONE]` | 是 | 新增 `crawl_run_logs` 或等价日志记录 | 已新增 `crawl_run_logs`，手动抓取记录 startedAt、status、数量统计和错误摘要 |
| 4.4 手动触发抓取 | `[DONE]` | 是 | 从页面或接口触发指定来源抓取 | 已支持 Source Management 页面和 `/api/crawl-sources/{id}/trigger` 触发指定已配置来源，成功/失败路径会记录日志 |
| 4.5 资源审核状态 | `[DONE]` | 是 | `resources` 支持 `PENDING/APPROVED/REJECTED` | 已为 `resources` 增加审核字段，页面可将资源改为 `APPROVED/REJECTED` 并持久保存 |
| 4.6 审核影响展示 | `[DONE]` | 是 | 被拒绝资源不进入至少一个学生侧或检索侧入口 | 已让资源列表、分页资源和聊天上下文检索默认只读取 `APPROVED` 资源 |
| 4.7 定时调度 | `[TODO]` | 否 | 固定间隔扫描 enabled sources | 本轮未做定时调度，不影响第 4 包完成边界 |

**用户故事**

- 管理员或教师能配置资讯源，启用/停用来源，手动触发抓取。
- 抓取后能看到运行日志、成功数量、失败原因。
- 新资源默认进入待审核，审核通过后才进入知识库或学生端展示。

**功能范围**

- 新增 `views/SourceManagement.tsx`，可挂教师端或管理员端。
- 后端在 `ResourceController` 或新增 `CrawlSourceController` 中提供来源配置和运行日志接口。
- 新增 `crawl_sources`、`crawl_run_logs`，`resources` 增加 `review_status`、`reviewed_by`、`reviewed_at`。
- `ResourceCrawlService` 先支持读取 DB 中的站点启用状态；站点规则仍可复用现有 `CrawlSiteRule`，不要求完全可视化配置 CSS selector。

**建议接口**

- `GET /api/crawl-sources`
- `POST /api/crawl-sources`
- `PUT /api/crawl-sources/{id}`
- `POST /api/crawl-sources/{id}/trigger`
- `GET /api/crawl-runs`
- `PUT /api/resources/{id}/review-status`

**验收标准**

- 页面能展示来源列表、启用状态、最近抓取时间。
- 手动触发一次抓取后能产生运行日志。
- 资源能从 `PENDING` 改为 `APPROVED/REJECTED`，拒绝资源不在学生侧推荐中出现。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 页面能维护知识源名称、基础地址、启用状态和备注说明。
- 手动触发抓取后能生成一条运行日志，日志展示状态、数量、错误摘要中的至少三项。
- 资源审核支持 `PENDING/APPROVED/REJECTED`，并至少影响资源列表或学生侧展示中的一个真实入口。
- 不要求第一版支持可视化 CSS selector、完整 cron 编辑器或跨站规则热更新。
- 4.1 至 4.6 全部 `[DONE]`；4.7 是可选项，不影响本功能包完成。

**可放宽项**

- 定时调度可以先做简单 `@Scheduled` 固定间隔扫描 enabled sources，不做 cron 编辑器。
- 站点规则可以先保留后端硬编码，页面只管理来源开关和说明。

### 5. [DONE] 课程关键词采集任务

**优先级理由**：`/Draft/毕设.md` 明确写了输入关键词、自动抓取行业动态并进行价值提炼。该功能作为新任务流，能让教师端多一个完整 AI 工作台。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 5.1 关键词任务数据结构 | `[DONE]` | 是 | 新增 `keyword_tasks` 和任务结果表 | 已新增 `keyword_tasks`、`keyword_task_items`，保存课程、关键词、创建人、状态、摘要和结果项 |
| 5.2 创建任务接口 | `[DONE]` | 是 | 教师基于课程提交关键词数组 | 已新增 `POST /api/keyword-tasks`，合法关键词生成任务；空关键词返回明确错误 |
| 5.3 任务运行逻辑 | `[DONE]` | 是 | 复用现有抓取或本地资源检索，再调用 AI 摘要 | 已支持同步运行任务，基于已审核资源检索并生成 AI 摘要，状态进入 `DONE/FAILED` 且失败有错误摘要 |
| 5.4 任务列表与详情页 | `[DONE]` | 是 | 新增页面或资源库 tab 展示任务 | 已新增教师端 `Keyword Tasks` 页面，可查看任务列表、状态、详情和错误信息 |
| 5.5 结果内容展示 | `[DONE]` | 是 | 展示标题、来源链接、摘录、AI 摘要、思政标签 | 任务详情展示标题、关键词、来源链接、摘录/AI 摘要和状态；标签以原始 `ideologyTags` 入库保留 |
| 5.6 接受结果入库 | `[DONE]` | 是 | 教师点击接受，把结果写入课程资源或课程知识库 | 已支持接受结果写入 `resources`，重复来源链接会复用既有资源并记录 item 的 `resourceId` |
| 5.7 外部资讯 API 扩展 | `[TODO]` | 否 | 接 NewsAPI/GDELT/arXiv 等 | 本轮未接真实外部资讯 API，不影响第 5 包完成边界 |

**用户故事**

- 教师在课程资源库或 Dashboard 输入关键词，例如“边缘计算、工业互联网、智能传感器”。
- 系统生成关键词采集任务，展示任务状态、抓取片段、AI 摘要和思政价值提炼。
- 教师可把结果加入课程知识库或作为教学案例素材。

**功能范围**

- 新增 `views/KeywordTaskCenter.tsx`，也可以先内嵌到 `ResourceLibrary.tsx` 的一个 tab。
- 新增 `keyword_tasks`、`keyword_task_items`。
- 后端复用 `ResourceCrawlService` 和 `AiIntelligenceService`，不另起复杂爬虫。
- 结果写入 `resources` 或 `subject_knowledge_sources` 时保留来源链接。

**建议接口**

- `POST /api/keyword-tasks`
- `GET /api/keyword-tasks?courseId=`
- `GET /api/keyword-tasks/{id}`
- `POST /api/keyword-tasks/{id}/run`
- `POST /api/keyword-tasks/{id}/items/{itemId}/accept`

**验收标准**

- 教师能创建任务并看到 `PENDING/RUNNING/DONE/FAILED` 状态。
- 完成任务至少展示标题、来源、摘录、AI 摘要、思政标签。
- 点击“加入课程知识库”后，课程知识点或资源库中能看到该条内容。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 教师能基于课程创建关键词任务，并查看任务列表、详情和状态。
- 任务详情至少展示关键词、来源标题、来源链接、摘录、AI 摘要、思政标签。
- 至少一条任务结果可被接受并写入课程资源或课程知识库。
- 不要求异步队列、外部多 API 全部真实可用或复杂失败重试。
- 5.1 至 5.6 全部 `[DONE]`；5.7 是可选项，不影响本功能包完成。

**可放宽项**

- 任务可以同步执行或短轮询，不要求队列。
- 外部 API 失败时可以展示“使用本地知识库回退结果”，但必须标注为 fallback。

### 6. [DONE] 专业知识-思政元素匹配审核

**优先级理由**：当前已有 `subject_knowledge`、`ideology_knowledge`、`subject_ideology_matches`，但缺审核流程。新增审核台能体现“AI 结果人工确认、修改、版本留痕”。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 6.1 匹配审核字段/表 | `[DONE]` | 是 | 为匹配保存状态、版本、审核人、审核意见 | 已为 `subject_ideology_matches` 增加 `PENDING/APPROVED/REJECTED`、版本、审核人、审核时间和备注字段，并新增审核历史表 |
| 6.2 待审核列表接口 | `[DONE]` | 是 | 查询待审核匹配，支持分页 | 已新增 `/api/matches/pending`，返回专业知识、思政元素、匹配理由、分数、状态，并支持状态筛选 |
| 6.3 批准/拒绝接口 | `[DONE]` | 是 | 教师可批准或拒绝匹配 | 已新增 approve/reject 接口，状态持久化并写入历史记录 |
| 6.4 修改匹配理由 | `[DONE]` | 是 | 教师可修改匹配理由或备注 | 已支持修改匹配理由和审核备注，空理由返回明确错误 |
| 6.5 审核历史记录 | `[DONE]` | 是 | 保存至少一次审核历史 | 已新增 `subject_ideology_match_reviews`，可按匹配 id 查询操作、前后状态、前后理由、操作人和时间 |
| 6.6 审核页面 | `[DONE]` | 是 | 新增 `MatchReview` 页面 | 已新增教师端 `Match Review` 页面和菜单入口，可查看、批准、拒绝、修改理由和查看历史 |
| 6.7 已批准匹配进入材料上下文 | `[DONE]` | 是 | 教学生成或 trace 至少一个入口使用/展示已批准匹配 | 已让知识图谱合成关系和轻量检索上下文只读取 `APPROVED` 匹配，审核通过后可在图谱/检索上下文中采用 |
| 6.8 一键回滚版本 | `[TODO]` | 否 | 回到某个历史版本 | 可查看历史即可；回滚不是本轮完成边界 |

**用户故事**

- AI 生成专业知识与思政元素匹配后，教师可审核。
- 教师能批准、拒绝、修改匹配理由。
- 被批准的匹配进入教学内容生成上下文，未批准的不进入正式材料。

**功能范围**

- 新增 `views/MatchReview.tsx`。
- `subject_ideology_matches` 增加 `status`、`version`、`reviewer_id`、`reviewed_at`、`review_comment`，或新增 `subject_ideology_match_reviews`。
- 教学内容生成读取 `APPROVED` 匹配；如果没有已批准数据，则回退现有逻辑，并在页面提示“Unreviewed AI matches used”。

**建议接口**

- `GET /api/matches/pending`
- `PUT /api/matches/{id}/approve`
- `PUT /api/matches/{id}/reject`
- `PUT /api/matches/{id}/revise`
- `GET /api/matches/history?subjectKnowledgeId=`

**验收标准**

- 页面可分页查看待审核匹配。
- 批准后状态改变，并能在教学材料 trace 或生成上下文中看到。
- 修改匹配理由后保留旧版本记录。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 教师端有匹配审核页面，支持待审核列表、批准、拒绝、修改理由。
- 后端保存审核状态和至少一条历史记录。
- 已批准匹配至少在教学材料生成上下文、材料 trace 或资源预览中的一个入口可见。
- 不要求复杂 diff、批量审批、完整回滚；这些应另起新功能包。
- 6.1 至 6.7 全部 `[DONE]`；6.8 是可选项，不影响本功能包完成。

**可放宽项**

- diff 展示可先用左右文本，不做复杂结构化 diff。
- 回滚可以只保留历史查看，不一定第一版就支持一键回滚。

### 7. [DONE] 模型配置页与场景路由可视化

**优先级理由**：验收清单里多模型能力仍未完整。当前后端已统一本地 OpenAI 兼容入口并已有 `ai.routes.*` 配置，新增可视化配置页能让“多模型能力”在答辩中可见。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 7.1 Provider 配置数据结构 | `[DONE]` | 是 | 保存 provider、baseUrl、model、enabled、keyConfigured | 已新增 `ai_provider_configs` 表、实体和迁移，DTO 只返回 `keyConfigured`，不回显密钥明文 |
| 7.2 Provider 列表与编辑接口 | `[DONE]` | 是 | 查询和保存 provider 配置 | 已新增 provider 列表、保存和配置完整性测试接口，支持启用状态、baseUrl、model、密钥写入和 timeout |
| 7.3 场景路由数据结构 | `[DONE]` | 是 | 保存 taskType -> provider/model | 已新增 `ai_route_configs` 表，覆盖 `chat/parse/ideology/question-gen/crawl/path/vision/embedding` |
| 7.4 路由展示与保存接口 | `[DONE]` | 是 | 查询和保存场景路由 | 已新增路由列表与保存接口，修改后进入 DB 持久化；暂不承诺立即热切全部 AI 链路 |
| 7.5 测试连接接口 | `[DONE]` | 是 | 对指定 provider 发起最小测试 | 已新增测试接口，当前 demo 口径为配置完整性检查，返回成功或明确缺失项 |
| 7.6 管理员配置页面 | `[DONE]` | 是 | 新增 `ModelSettings` 页面 | 已新增管理员 `Model Settings` 菜单和页面，展示 providers、routes、测试按钮、保存反馈和运行时生效说明 |
| 7.7 AI 助手运行时提示 | `[DONE]` | 否 | 普通用户只读查看当前模型信息 | `AIAssistant` 顶部与空态已展示 Local OpenAI-Compatible、`gpt-5.4` 和 `http://localhost:8317/v1` |
| 7.8 真实多 provider 全链路联调 | `[TODO]` | 否 | DeepSeek/Gemini/Doubao 全部真实调用 | 不作为本功能包完成要求 |

**用户故事**

- 管理员能看到聊天、解析、题目生成、资源抓取、学习路径、视觉分析分别使用哪个模型。
- 管理员能配置 API base、模型名、启用状态，并执行测试连接。
- 普通教师能在 AI 助手页面看到当前运行时信息，但不能修改。

**功能范围**

- 新增 `views/ModelSettings.tsx`，挂到 admin 菜单，或教师端 settings 中仅管理员可见。
- 后端新增 `AiProviderController`。
- 第一版可以只读取 `application.yml` 和环境变量，并把修改保存到 `ai_provider_configs` 表，提示“部分配置重启后生效”。
- `AiIntelligenceService` 不要求大改；若接入运行时读取成本过高，可以先完成展示和测试连接。

**建议接口**

- `GET /api/admin/ai-providers`
- `PUT /api/admin/ai-providers/{provider}`
- `POST /api/admin/ai-providers/{provider}/test`
- `GET /api/admin/ai-routes`
- `PUT /api/admin/ai-routes/{taskType}`

**验收标准**

- 页面展示至少 `chat/parse/question-gen/crawl/path/vision/embedding` 七类任务路由。
- 测试连接能返回成功或明确错误。
- 密钥不回显，只显示是否已配置。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 管理员能看到 provider 列表和 `chat/parse/question-gen/crawl/path/vision/embedding` 路由表。
- 支持保存 provider 启用状态、API base、模型名和密钥是否已配置状态。
- 至少一个 provider 支持测试连接，并把成功或失败原因展示给用户。
- 不要求运行时热切换所有服务；如需重启生效，页面和文档说明即可。
- 7.1 至 7.6 全部 `[DONE]`；7.7、7.8 是可选项，不影响本功能包完成。

**实现记录**

| 时间 | 功能包 | 范围 | 状态变化 | 说明 | 相关文档 |
| --- | --- | --- | --- | --- | --- |
| 2026-04-25 02:35 | 模型配置页与场景路由可视化 | 7.1-7.6 | 第 7 包从 `[TODO]` 到 `[DONE]` | 完成 provider/route 配置表、admin API、配置完整性测试接口、管理员 Model Settings 页面、菜单权限与定向测试；运行时 DB 热切换和真实多 provider 联调仍为后续项 | `Draft/2026-04-25-0235-model-settings.md` |
| 2026-04-25 03:25 | AI 助手运行时提示 | 7.7 | 可选项从 `[TODO]` 到 `[DONE]` | 确认 `AIAssistant` 已展示当前聊天运行时 provider/model/base URL，普通用户可只读识别本地 OpenAI-compatible 运行时；未做真实多 provider 联调 | `Draft/2026-04-25-0325-ai-runtime-indicator.md` |
| 2026-04-25 02:45 | 管理员控制台：用户、课程、学生绑定 | 8.1-8.7 | 第 8 包从 `[TODO]` 到 `[DONE]` | 完成 course_students 表、admin 用户管理与课程学生绑定接口、Admin Console 页面、学生端课程绑定过滤和定向测试；教师课程归属与班级组织仍为后续项 | `Draft/2026-04-25-0245-admin-console.md` |

**可放宽项**

- 不做密钥高级加密；可以用环境变量优先、DB 仅存 demo key，并在文档说明。
- 修改后不一定实时影响所有服务，只要页面明确生效策略。

### 8. [DONE] 管理员控制台：用户、课程、学生绑定

**优先级理由**：管理员角色目前存在但功能不足。该模块新增页面多、演示直观，适合作为 P2 但可见工作量很高的功能。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 8.1 管理员菜单入口 | `[DONE]` | 是 | admin 登录后可进入 `Admin Console` | 已新增 admin-only `Admin Console` 菜单、能力位和 TeacherShell 懒加载入口 |
| 8.2 用户分页列表 | `[DONE]` | 是 | 展示用户名、姓名、角色、状态、创建时间 | 已支持用户分页列表，并支持关键词与角色过滤 |
| 8.3 新增/编辑用户 | `[DONE]` | 是 | 管理员可创建用户、编辑基础资料 | 已支持创建用户、编辑基础资料、手动设置/重置密码 |
| 8.4 启用/禁用用户 | `[DONE]` | 是 | 修改用户状态 | 已支持状态开关；既有登录链路会拒绝禁用用户 |
| 8.5 课程学生绑定表 | `[DONE]` | 是 | 新增 `course_students` 或等价关系 | 已新增 `course_students` 主 schema 与迁移脚本，保存课程和学生多对多关系 |
| 8.6 课程学生绑定页面/接口 | `[DONE]` | 是 | 管理员给课程添加/移除学生 | 已新增课程学生列表、添加、移除接口和 Admin Console 绑定页 |
| 8.7 学生端按绑定展示课程 | `[DONE]` | 是 | 学生首页或资源库按绑定课程过滤 | 已新增学生课程接口，学生端 ResourceLibrary 按绑定课程读取 |
| 8.8 教师课程归属管理 | `[TODO]` | 否 | 管理员调整课程 teacherId | 可用现有课程创建能力替代，不影响本功能包完成 |
| 8.9 班级/组织管理 | `[DEFERRED]` | 否 | 班级、学院、组织树 | 不属于本 PRD 范围 |

**用户故事**

- 管理员能创建教师、学生、管理员账号。
- 管理员能把教师绑定到课程，把学生加入课程。
- 学生登录后只能看到被绑定课程相关内容。

**功能范围**

- 新增 `views/AdminConsole.tsx`。
- 新增 `course_students` 表；课程教师可继续用 `courses.teacher_id`。
- `UserController` 或新增 `AdminController` 提供用户分页、启停、重置密码、课程绑定接口。
- `RoleAccessService` 可先用于前端菜单和部分后端接口校验，不要求全局权限体系一次完成。

**建议接口**

- `GET /api/admin/users`
- `POST /api/admin/users`
- `PUT /api/admin/users/{id}`
- `PUT /api/admin/users/{id}/status`
- `GET /api/admin/courses/{id}/students`
- `POST /api/admin/courses/{id}/students`
- `DELETE /api/admin/courses/{id}/students/{studentId}`

**验收标准**

- 管理员能新增一个学生并绑定课程。
- 该学生登录后能在学生首页或资源库看到对应课程。
- 禁用用户后无法登录或无法进入系统。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 管理员菜单中有 `Admin Console` 页面。
- 支持用户分页列表、新增用户、编辑基础资料、启用/禁用用户。
- 支持课程绑定学生，并在学生端至少一个真实页面按绑定关系展示课程或资源。
- 不要求班级、组织架构、邀请邮件、细粒度权限矩阵。
- 8.1 至 8.7 全部 `[DONE]`；8.8 是可选项，8.9 固定 `[DEFERRED]`，不影响本功能包完成。

**可放宽项**

- 第一版密码可由管理员手动输入，不做邮件邀请。
- 不做复杂班级管理，课程-学生绑定即可。

### 9. [DONE] 知识图谱关系编辑、重复提示与撤销

**优先级理由**：图谱是核心展示模块，当前已有节点和关系基础能力，补关系编辑、重复提示、撤销能提升演示完整度。但它属于已有模块深化，排在新增页面之后。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 9.1 节点详情关系列表 | `[DONE]` | 是 | 在图谱节点详情展示入边/出边 | 已在节点详情抽屉展示入边/出边、相邻节点、关系类型、只读合成关系标记 |
| 9.2 关系类型选择 | `[DONE]` | 是 | 新建或编辑关系时选择类型 | 新增和编辑均使用现有类型集合；后端拒绝非法类型并兼容旧中文类型归一 |
| 9.3 重复关系提示 | `[DONE]` | 是 | 后端校验重复关系，前端展示提示 | 相同 from/to/type 创建或编辑返回 `Duplicate relation`，前端 message 展示错误 |
| 9.4 删除关系入口 | `[DONE]` | 是 | 前端可删除单条关系 | 真实关系可在节点详情中删除，删除后从图谱状态移除；负 id 合成关系只读 |
| 9.5 关系编辑接口 | `[DONE]` | 是 | 修改关系类型或描述 | 已新增 `PUT /api/knowledge/relations/{id}`，支持关系类型、描述、权重更新并记录日志 |
| 9.6 操作日志数据结构 | `[DONE]` | 是 | 记录关系删除/编辑前后值 | 已新增 `knowledge_change_logs`，记录关系更新/删除 before/after JSON 和 undone 状态 |
| 9.7 最近一次撤销 | `[DONE]` | 是 | 支持撤销最近一次关系删除或编辑 | 已新增最近一次关系变更撤销接口和前端按钮，撤销后重新加载图谱 |
| 9.8 节点编辑增强 | `[TODO]` | 否 | 编辑节点章节、标签、描述等 | 不影响关系治理完成 |

**用户故事**

- 教师能在图谱详情抽屉中看到与节点相关的关系。
- 教师能新增、删除、编辑关系类型。
- 误删后可以撤销最近操作。

**功能范围**

- `KnowledgeGraph.tsx` 节点详情抽屉增加关系列表、关系类型选择和删除按钮。
- 后端 `KnowledgeController` 已有关系接口，可补重复关系 409、撤销接口。
- 新增 `knowledge_change_logs`，记录节点/关系新增、删除、编辑的 before/after。

**建议接口**

- `PUT /api/knowledge/relations/{id}`
- `GET /api/knowledge/change-logs?limit=`
- `POST /api/knowledge/change-logs/{id}/undo`

**验收标准**

- 重复关系有明确提示。
- 删除关系后图谱刷新，撤销后关系恢复。
- 节点详情能按关系类型展示相邻节点。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- 节点详情抽屉展示相关关系列表，并支持删除关系。
- 新增或编辑关系时能选择关系类型，重复关系返回明确提示。
- 至少支持最近一次关系删除撤销，并能在图谱中恢复显示。
- 不要求多人协同冲突、任意历史回放或图谱自动演化。
- 9.1 至 9.7 全部 `[DONE]`；9.8 是可选项，不影响本功能包完成。

**可放宽项**

- 撤销只支持最近 20 条或 5 分钟内操作。
- 不做多人并发冲突处理。

### 10. [DONE] README、演示脚本与测试用例表

**优先级理由**：在主要新增功能完成后再写，避免脚本脱离真实链路。该项对论文和答辩很重要，但不应过早开始。

**子功能状态表**

| 子功能点 | 状态 | 必做 | 明确范围 | 完成边界 |
| --- | --- | --- | --- | --- |
| 10.1 README 启动说明 | `[DONE]` | 是 | 前端、后端、数据库、本地 AI 配置启动说明 | README 已补 PowerShell 启动命令、端口、配置文件、数据库与 AI/MinerU/Qdrant 配置 |
| 10.2 演示账号与数据说明 | `[DONE]` | 是 | README 或 Draft 写清教师/学生/admin 演示账号和数据来源 | 已说明仓库无固定 seed 账号，按注册、Admin Console 或本地库角色调整准备演示账号与课程绑定 |
| 10.3 教师主线演示脚本 | `[DONE]` | 是 | 上传解析、教学生成、资源库、知识图谱 | `Draft/2026-04-25-0305-demo-script.md` 已覆盖教师上传、生成、资源库、图谱关系治理和预警步骤 |
| 10.4 学生主线演示脚本 | `[DONE]` | 是 | 学习报告、路径推荐、AI 提问、反馈建议 | 已覆盖 Student Home、Course Library、Knowledge Graph、AI Assistant 与行为上报回看 |
| 10.5 知识库更新主线脚本 | `[DONE]` | 是 | 来源抓取、审核、知识库引用 | 已覆盖 Source Management、Keyword Tasks、Match Review、APPROVED 匹配进入图谱/RAG，外部失败说明 fallback |
| 10.6 管理员主线脚本 | `[DONE]` | 是 | 用户管理、课程绑定、模型配置 | 已覆盖 Admin Console 用户/课程绑定和 Model Settings provider/route 配置 |
| 10.7 功能测试用例表 | `[DONE]` | 是 | 登录、上传、生成、图谱、AI、学生行为、预警、资源审核 | `Draft/2026-04-25-0305-test-cases.md` 已列功能测试表，包含前置条件、步骤、预期结果、实际结果栏 |
| 10.8 异常测试用例表 | `[DONE]` | 是 | 上传失败、AI 失败、抓取失败、无权限、空数据 | 已列异常测试表，覆盖登录失败、上传失败、AI/抓取失败、空数据、重复关系和越权入口 |
| 10.9 Playwright 自动化 | `[DEFERRED]` | 否 | 自动化 smoke test | 不属于赶进度必做范围 |

**用户故事**

- 后续 agent 或答辩者能按文档启动系统、加载演示数据、走完整演示。
- 论文“系统测试”章节有功能测试表和异常测试表。

**功能范围**

- 扩写 `README.md`：前后端启动、数据库初始化、演示账号、常见问题。
- 新增 `Draft/YYYY-MM-DD-HHMM-demo-script.md`：教师主线、学生主线、知识库更新主线、管理员主线。
- 新增 `Draft/YYYY-MM-DD-HHMM-test-cases.md`：功能测试用例表、异常场景、预期结果。

**验收标准**

- 新上下文 agent 按 README 能启动项目。
- 演示脚本每一步能对应真实页面。
- 测试用例覆盖登录、上传解析、教学生成、知识图谱、AI 助手、学生行为、预警、资源审核。

**大功能包完成边界（达到即改标题为 `[DONE]`）**

- `README.md` 包含 PowerShell 启动命令、数据库初始化、前后端配置、演示账号和常见问题。
- `Draft/YYYY-MM-DD-HHMM-demo-script.md` 覆盖教师、学生、知识库更新、管理员四条演示主线。
- `Draft/YYYY-MM-DD-HHMM-test-cases.md` 覆盖功能测试表和异常场景表。
- 演示脚本中每一步都能对应当前真实页面；不写尚未实现的未来页面。
- 10.1 至 10.8 全部 `[DONE]`；10.9 固定 `[DEFERRED]`，不影响本功能包完成。

**可放宽项**

- 不强制 Playwright 自动化。
- 不强制 Testcontainers；可以用手工测试表 + 少量后端 controller test。

## 页面与菜单建议

### 教师端新增菜单

- `Alerts`：预警中心，对应 `AlertConsole.tsx`。
- `Keyword Tasks`：关键词采集，对应 `KeywordTaskCenter.tsx`。
- `Match Review`：AI 匹配审核，对应 `MatchReview.tsx`。
- `Source Management`：知识源与审核，对应 `SourceManagement.tsx`。

### 学生端新增入口

- `Learning Report`：可内嵌 `StudentHome.tsx`，不用单独新菜单。
- `Review Today`：今日复习卡片，点击跳到知识图谱并高亮节点。
- `Feedback`：预警反馈条，点击跳到 AI 助手或推荐资源。

### 管理员新增入口

- `Admin Console`：用户与课程绑定。
- `Model Settings`：模型配置与场景路由。
- 管理员可复用 `TeacherShell`，不用新建完整 `AdminShell`，除非后续页面明显增多。

## 数据表最小新增清单

按功能分批新增，不建议一次性提交所有表。

- `student_activity_events`：学生行为事件流。
- `student_alert_records`：预警记录与处理状态。
- `course_chapters`：课程章节。
- `chapter_material_bindings`：章节与材料版本绑定，可替代直接改 trace。
- `crawl_sources`：知识源配置。
- `crawl_run_logs`：抓取运行日志。
- `keyword_tasks`、`keyword_task_items`：关键词采集任务。
- `subject_ideology_match_reviews`：匹配审核历史。
- `ai_provider_configs`、`ai_route_configs`：模型配置与场景路由。
- `course_students`：学生课程绑定。
- `knowledge_change_logs`：图谱操作撤销。

## Agent 执行约束

- 每个 agent 默认只领取一个功能包中的 1-3 个子功能点；除非用户明确要求，不要一次性实现整个大功能包。
- 子功能点做到“完成边界”后必须立刻把该子功能点改为 `[DONE]`；没做到就保持 `[DOING]` 或 `[BLOCKED]`，不要用文字模糊带过。
- 大功能包标题状态由子功能点汇总得出，不能因为只完成一两个子功能点就把整个大功能包改成 `[DONE]`。
- 每个 agent 尽量避免跨功能包同时修改 `services/api.ts`、`types.ts`、Shell 菜单造成冲突。
- 每个功能包开始前先读对应现有文件，不要大范围重构。
- 如果只是新增页面，优先复用现有 `request/get/post/put/del` 封装。
- 后端新增接口保持 `Result<T>` 包装和当前 controller 风格。
- 数据库迁移脚本继续放 `backend/src/main/resources/migration_<short_name>.sql`。
- 新增功能完成后必须：
  - 更新 `Draft/2026-04-16-1337-acceptance-checklist.md` 对应状态和维护记录。
  - 更新本文对应子功能点状态和“PRD 实现记录”。
  - 新增一份 `Draft/YYYY-MM-DD-HHMM-<short-name>.md` 变更说明。
  - 至少执行相关后端测试或 `npm run build`；不能执行时说明原因。

## 风险与注意事项

- 当前部分文档和历史注释曾出现编码问题，读取文档时使用 `-Encoding UTF8`，不要直接删除疑似乱码。
- `rg` 在当前环境可能被拒绝执行，可用 PowerShell `Get-ChildItem` 和 `Select-String` 替代。
- 学生预警和视觉分析是答辩亮点，但真实摄像头和视觉模型不稳定；建议第一版明确支持 demo mode。
- 多模型配置页容易拖成复杂配置中心；第一版只做展示、编辑、测试连接和路由说明。
- 管理员权限不要一次性做全局拦截，否则容易影响现有演示链路；先保证菜单可见性和关键接口最小校验。
- 不要把“看起来完成”的 demo 能力标为 `[x]`；验收清单中应写明 demo、fallback 或未真实联调。

## PRD 实现记录

| 时间 | 功能包 | PRD 状态 | 对应 checklist 维护 | 变更文档 |
| --- | --- | --- | --- | --- |
| 2026-04-24 21:00 | PRD 初始化 | 全部 `[TODO]` | 本次仅写规划，不改验收状态 | `Draft/2026-04-24-2100-feature-expansion-prd.md` |
| 2026-04-24 23:12 | 学生学习行为采集与学习报告 | 1.1-1.6 | 第 1 包从 `[TODO]` 到 `[DONE]` | 完成事件流表、批量上报、报告/最近活动接口、学生端 tracking 和首页报告卡片；1.7 答题正确率仍为可选 `[TODO]` | 已更新学生学习支持与学生监测条目 | `Draft/2026-04-24-2312-student-learning-report.md` |

后续追加记录时使用以下口径：

| 时间 | 功能包 | 子功能点 | PRD 状态变更 | 完成边界命中情况 | checklist 维护 | 变更文档 |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-04-25 00:20 | 学生预警中心与学生反馈 | 2.1-2.6 | 第 2 包从 `[TODO]` 到 `[DONE]` | 完成预警记录表、生成/汇总/列表/状态更新/学生反馈接口、教师 AlertConsole 和学生首页反馈建议；2.7/2.8 视觉项仍为可选 `[TODO]` | 已更新学生学习支持、学生监测与预警、测试验收条目 | `Draft/2026-04-25-0020-student-alert-feedback.md` |
| 2026-04-25 01:35 | 课程章节、知识点绑定与资源状态汇总 | 3.1-3.6 | 第 3 包从 `[TODO]` 到 `[DONE]` | 完成课程章节表、章节列表/新增/编辑接口、材料版本章节归属、资源库章节分组、上传/编辑页章节选择和课程状态汇总；3.7 章节知识点绑定仍为可选 `[TODO]` | 已更新课程资源库与教师工作台、测试验收条目 | `Draft/2026-04-25-0135-course-chapters-material-status.md` |
| 2026-04-25 02:05 | 知识源配置、抓取日志与人工审核 | 4.1-4.6 | 第 4 包从 `[TODO]` 到 `[DONE]` | 完成知识源配置表/接口、抓取运行日志、指定来源手动触发、资源审核状态和 Source Management 页面；4.7 定时调度仍为可选 `[TODO]` | 已更新思政知识库、课程资源审核与测试验收条目 | `Draft/2026-04-25-0205-crawl-source-review.md` |
| 2026-04-25 02:17 | 课程关键词采集任务 | 5.1-5.6 | 第 5 包从 `[TODO]` 到 `[DONE]` | 完成关键词任务表/接口、同步运行、已审核资源检索、AI 摘要、教师端 Keyword Tasks 页面和接受结果入库；5.7 外部资讯 API 仍为可选 `[TODO]` | 已更新专业知识与思政价值挖掘、角色菜单与测试验收条目 | `Draft/2026-04-25-0217-keyword-tasks.md` |
| 2026-04-25 02:27 | 专业知识-思政元素匹配审核 | 6.1-6.7 | 第 6 包从 `[TODO]` 到 `[DONE]` | 完成匹配审核字段/历史表、列表/批准/拒绝/修改/历史接口、教师端 Match Review 页面，并让知识图谱和检索上下文只采用 `APPROVED` 匹配；6.8 回滚仍为可选 `[TODO]` | 已更新专业知识与思政价值挖掘、角色菜单与测试验收条目 | `Draft/2026-04-25-0227-match-review.md` |
| 2026-04-25 02:45 | 管理员控制台：用户、课程、学生绑定 | 8.1-8.7 | 第 8 包从 `[TODO]` 到 `[DONE]` | 完成 admin-only 菜单、用户分页/创建/编辑/启停、课程学生绑定表与接口、Admin Console 页面，以及学生端 ResourceLibrary 绑定课程过滤；8.8 教师课程归属仍为可选 `[TODO]`，8.9 固定 `[DEFERRED]` | 已更新角色体系、学生学习支持、课程资源库、数据配置与测试验收条目 | `Draft/2026-04-25-0245-admin-console.md` |
| 2026-04-25 02:55 | 知识图谱关系编辑、重复提示与撤销 | 9.1-9.7 | 第 9 包从 `[TODO]` 到 `[DONE]` | 完成节点详情关系列表、关系类型编辑、重复关系提示、关系删除、关系变更日志和最近一次撤销；9.8 节点编辑增强仍为可选 `[TODO]` | 已更新课程知识图谱、数据配置与测试验收条目 | `Draft/2026-04-25-0255-knowledge-relation-governance.md` |
| 2026-04-25 03:05 | README、演示脚本与测试用例表 | 10.1-10.8 | 第 10 包从 `[TODO]` 到 `[DONE]` | 完成 README 本地启动/配置/账号准备说明、四条演示主线脚本、功能测试用例表和异常测试用例表；10.9 Playwright 自动化保持 `[DEFERRED]` | 已更新数据配置、部署说明、测试验收与演示脚本条目 | `Draft/2026-04-25-0305-demo-readme-testcases.md` |
| 2026-04-25 17:30 | 教师 Dashboard 聚合工作台 | P0-2 | `2026-04-25-1633-prd-demo-richness-plan` P0-2 从未完成到已完成 | 完成 Dashboard overview 聚合接口、待办卡片、材料发布统计、最近解析任务和学习事件趋势；未做复杂 BI、实时推送和浏览器手动验收 | 已更新课程资源库与教师工作台、测试验收条目 | `Draft/2026-04-25-1730-teacher-dashboard-overview.md` |

## 下一位 agent 的接手提示

最建议第一个实现的功能包是“学生学习行为采集与学习报告”，因为它能支撑预警、复习推荐和学习路径三个后续功能。相关路径：`views/StudentHome.tsx`、`layouts/StudentShell.tsx`、`services/api.ts`、`types.ts`、`backend/src/main/java/com/smartedu/entity/StudentActivity.java`、`backend/src/main/java/com/smartedu/service/AlertService.java`、`backend/src/main/resources/schema.sql`。

第二个功能包建议直接做“预警中心与学生反馈”，相关路径：`backend/src/main/java/com/smartedu/service/AlertService.java`、新增 `AlertController`、新增 `views/AlertConsole.tsx`，并在 `TeacherShell` 菜单挂入口。这样两轮之后就能演示学生侧行为、教师侧监测和学生侧反馈闭环。
