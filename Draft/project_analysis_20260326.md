# 智教思政 · 项目功能分析与开发进度报告

> 生成时间：2026-03-26  
> 项目路径：`e:\study\smart-education-ideology`

---

## 一、毕设需求解读：四大子系统功能点

根据 `Draft/毕设.md`，本项目是一个面向物联网专业的**课程思政智能化教学平台**，包含以下四个独立但相互关联的子系统。

---

### 📌 子系统 1：思政价值挖掘系统（核心）

**定位**：输入专业关键词 → 爬取权威资讯 → 大模型语义分析 → 输出思政价值

| 功能点 | 详细描述 |
|--------|----------|
| 关键词输入 | 支持输入物联网相关专业术语和关键词 |
| 爬虫采集 | 实时抓取权威新闻、政策文件、行业动态（人民网、建设学习网等） |
| 大模型分析 | 调用 DeepSeek / 国产大模型，提取国家战略导向、社会应用价值、就业趋势 |
| 多模型支持 | 支持用户选择不同 AI 提供商（DeepSeek、Gemini 等） |
| 交互式对话 | 支持基于大模型的交互问答 |
| 资源追溯 | 每条知识点附带原始来源链接 |
| 定期自动更新 | 知识库支持定时爬取更新 |

---

### 📌 子系统 2：课程知识图谱系统（核心）

**定位**：Excel 模板导入 → 自动生成知识图谱 → 可视化展示 → 大模型问答

| 功能点 | 详细描述 |
|--------|----------|
| Excel 模板生成 | 生成含知识节点、关系、属性信息的 Excel 模板文件 |
| Excel 导入建图 | 解析 Excel 自动构建知识图谱结构 |
| 知识图谱可视化 | 支持节点、关系的图形化交互展示 |
| 节点拖拽管理 | 支持节点位置调整与位置持久化保存 |
| 语义问答 | 学生基于图谱进行自然语言提问 |
| 概念解释 | 查询特定知识点的技术定义与思政价值 |
| 知识路径查询 | 可视化查看知识点之间的学习路径 |
| 个性化学习推荐 | 基于知识图谱 DAG 结构推荐学习路径 |
| 图谱增量更新 | 支持新内容同步到现有图谱 |

---

### 📌 子系统 3：智能教学内容生成系统（教师端）

**定位**：教师上传文献 → MinerU 结构化解析 → 大模型生成教学资源

| 功能点 | 详细描述 |
|--------|----------|
| 多格式文件上传 | 支持 PDF、DOCX、PPT、XLS 等格式 |
| MinerU 文档解析 | 结构化解析文档，提取知识点 |
| 知识点识别与语义聚类 | 大模型向量接口进行语义聚类 |
| 自动生成教学讲义 | 大模型自动输出教学讲义 |
| 自动生成案例内容 | 生成贴合专业的教学案例 |
| 思政融入材料生成 | 自动挖掘并融合思政元素 |
| 自动生成考核题目 | 生成配套习题与考核题 |
| 外部资讯接入 | 调用 NewsAPI / GDELT / arXiv 获取前沿内容 |
| 可视化编辑界面 | 教师可与 AI 协作编辑课程内容 |

---

### 📌 子系统 4：学生自主学习监测与预警系统（学生端）

**定位**：采集学习行为 → 大模型分析状态 → 实时预警 + 个性化反馈

| 功能点 | 详细描述 |
|--------|----------|
| 学习行为数据采集 | 实时记录答题正确率、学习时长、鼠标停留时间、学习路径变化 |
| 摄像头视频流采集 | 调用计算机摄像头采集学习过程视频流 |
| 关键帧图像提取 | 自动从视频流中定时抽取关键帧 |
| 图像情绪语义分析 | 调用大模型 API（豆包、DeepSeek）分析表情、情绪、专注度 |
| 认知状态识别 | 识别学生的认知困惑、专注度下降、学习疲劳情况 |
| 预警触发与等级判断 | 基于多维度指标计算预警等级（轻度/中度/重度） |
| 个性化学习建议 | 大模型生成个性化复习建议和学习引导 |
| 心理调节提示 | 大模型输出心理调节指导内容 |
| 仪表盘展示 | 教师端查看所有学生预警状态全览 |

---

## 二、当前代码已完成的功能盘点

### 后端（Java Spring Boot）

#### 已完成的基础架构
- Spring Boot 3.x 项目骨架，JWT 鉴权，MyBatis-Plus ORM
- 数据库设计（schema.sql）：完整的 12+ 张表
- 新数据模型：学科知识/思政知识/匹配关系三表分离架构
- 多 AI 提供商策略路由：DeepSeek / OpenAI 兼容 API / Gemini（预留）三方切换
- 角色权限控制（RoleAccessService）：TEACHER / STUDENT / ADMIN 三角色

#### 已完成的核心服务

| 服务 | 文件 | 完成度 |
|------|------|--------|
| 用户认证（注册/登录/JWT） | AuthService.java | 完整 |
| 仪表盘数据统计 | DashboardService.java | 完整 |
| 资源爬虫（人民网/建设学习网） | ResourceCrawlService.java | 完整（异步、状态机） |
| 知识图谱构建与可视化 | KnowledgeService.java | 完整 |
| 知识入库同步 | KnowledgeIngestionService.java | 完整 |
| 知识检索（RAG） | KnowledgeRetrievalService.java | 完整 |
| AI 对话（含 RAG 增强） | ChatService.java | 完整 |
| AI 智能分析（思政价值挖掘） | AiIntelligenceService.java | 完整（MinerU 为模拟） |
| 文档上传与解析任务 | UploadController.java | 完整（MinerU 为模拟） |
| 学习路径推荐 | PathRecommendService.java | 完整（DAG + 优先级队列） |
| 预警等级计算 | AlertService.java | 完整（三维评估 + 情绪权重） |
| 课程管理 | CourseService.java | 完整 |
| 资源管理 CRUD | ResourceService.java | 完整 |

#### 已完成的 API 接口

| 控制器 | 已实现接口 |
|--------|-----------|
| AuthController | 登录、注册、获取当前用户、Bootstrap 数据 |
| ChatController | 会话列表、创建会话、发消息、删除会话、选段解释 |
| KnowledgeController | 图谱查询、节点搜索、更新坐标、创建关系 |
| ResourceController | 资源列表/增删改查、爬虫启动/停止/状态 |
| UploadController | 文件上传、任务状态查询 |
| DashboardController | 统计卡片、课程列表、系统动态、趋势图 |
| CourseController | 课程知识点查询、创建课程 |
| UserController | 头像上传 |

---

### 前端（React + TypeScript）

#### 已完成的界面与功能

| 模块 | 文件 | 完成度 |
|------|------|--------|
| 登录/注册页 | views/Auth.tsx | 完整（含角色选择） |
| 教师 Dashboard | views/Dashboard.tsx | 完整（统计卡片/趋势图/动态/课程表） |
| 知识图谱可视化 | views/KnowledgeGraph.tsx | 完整（节点拖拽/搜索/连线/爬虫控制） |
| AI 助手对话 | views/AIAssistant.tsx | 完整（多会话/历史记录） |
| 文档上传与解析 | views/ResourceUpload.tsx | 完整（进度追踪/状态展示） |
| 课程资源库 | views/ResourceLibrary.tsx | 完整（列表/搜索/分类） |
| 教师 Shell（布局+路由） | layouts/TeacherShell.tsx | 完整 |
| 学生 Shell（布局+路由） | layouts/StudentShell.tsx | 完整（视图守卫/角色过滤） |
| 侧边栏 + 顶部 Header | components/Sidebar.tsx / Header.tsx | 完整（头像上传/角色区分） |
| 认证上下文 | contexts/AuthContext.tsx | 完整（Bootstrap 驱动） |
| 统一 API 服务层 | services/api.ts | 完整（Token 管理/全类型定义） |

---

## 三、尚未实现的功能清单

### 高优先级（毕设核心，必须实现）

| 功能 | 所属子系统 | 当前状态 |
|------|----------|----------|
| MinerU API 真实调用 | 子系统 3 | 代码中有 TODO，使用模拟数据代替 |
| Excel 模板生成与导入建图 | 子系统 2 | 前后端均未实现 |
| 摄像头视频流采集 + 关键帧提取 | 子系统 4 | 后端有预警逻辑，前端无摄像头集成 |
| 图像情绪分析（大模型视觉 API） | 子系统 4 | 仅有文字情绪状态字段，无图像分析 |
| 学生学习监测前端页面 | 子系统 4 | 学生端无此视图 |
| 多 AI 模型前端选择切换 | 全部子系统 | 后端支持多模型，前端无选择 UI |

### 中优先级（增强完整度）

| 功能 | 所属子系统 | 当前状态 |
|------|----------|----------|
| 图谱知识路径前端可视化 | 子系统 2 | 后端 PathRecommendService 已就绪，前端无展示 |
| 个性化学习推荐展示 | 子系统 2/4 | 后端服务就绪，前端无入口 |
| 外部资讯接入（NewsAPI/arXiv） | 子系统 3 | 未实现 |
| 考核题目自动生成 UI | 子系统 3 | 未实现 |
| 教学讲义自动生成界面 | 子系统 3 | 可视化编辑界面未实现 |
| 学生端 Dashboard 首页 | 子系统 4 | 学生端无 Dashboard，直接进入知识图谱 |
| 预警数据的教师端可视化 | 子系统 4 | 仅有数量统计，无详细预警列表 |

### 低优先级（锦上添花）

| 功能 | 所属子系统 |
|------|----------|
| 知识图谱 Excel 导入历史管理 | 子系统 2 |
| 定时任务配置界面 | 子系统 1 |
| 学生历史学习报告导出 | 子系统 4 |
| 豆包 Doubao API 接入 | 全部 |
| Gemini API 完整实现（目前回退到 OpenAI） | 全部 |

---

## 四、合理的功能实现优先顺序

### 第一阶段（已完成）

- 用户认证与角色权限（JWT）
- 爬虫采集 + 知识库构建（状态机管理）
- 知识图谱可视化（节点拖拽/连线）
- AI 对话（RAG 检索增强）
- 文档上传与异步解析框架
- 教师端 Dashboard
- 前端角色分流路由（TeacherShell / StudentShell）

### 第二阶段（✅ 已完成，2026-03-26）

**Step 1 – MinerU API 改为 AI 结构化解析** ✅
`parseDocument()` 改为：① 读取文件真实内容（UTF-8/GBK）；② 调用接入的大模型 AI，使用结构化提示词约束输出（章节/知识点/教学重点/思政关联），效果对齐 MinerU 格式；③ 文件不可读时回退到文件名推断模式，降级输出占位内容。
（已落实用户备注：使用接入的 AI 进行模拟，增强对结构化数据的约束）

**Step 2 – 多 AI 模型前端选择 UI** ✅
在 AI 助手页面顶部增加模型选择下拉框（DeepSeek / OpenAI / Gemini），选择持久化到 localStorage；切换模型时重置当前会话；创建会话时将 `aiModel` 参数传给后端，后端已支持多模型路由。

**Step 3 – Excel 图谱模板导入功能** ✅
后端新增 `KnowledgeExcelService`（Apache POI），实现：
- `GET /api/knowledge/excel/template`：生成含「知识节点」「知识关系」「填写说明」三 Sheet 的标准模板
- `POST /api/knowledge/excel/import`：解析 Excel 批量创建节点和关系，返回统计结果
前端知识图谱页面增加「模板下载」和「导入」按钮，导入完成后自动刷新图谱。

**Step 4 – 学生端独立首页 StudentHome** ✅
新建 `views/StudentHome.tsx`（非 Dashboard 克隆）：欢迎横幅、学习统计卡片（图谱节点数/推荐路径节点数/近期动态）、快捷导航、近期系统动态。`StudentShell` 默认视图改为 `STUDENT_HOME`，Sidebar 增加「学习首页」导航项。
（已落实用户备注：学生端首页独立设计，与教师端 Dashboard 完全分离）

**Step 5 – 学习路径推荐前端展示** ✅
后端新增 `PathRecommendController`（`POST /api/path/recommend`），调用已有的 DAG 推荐服务，返回节点 ID 列表和名称列表。学生首页展示推荐路径，点击「在知识图谱中查看」可跳转并高亮对应节点（知识图谱增加 `highlightNodeIds` prop，高亮节点显示金色虚线发光边框）。

### 第三阶段（增强系统完整性）

**Step 6 – 摄像头 + 情绪分析**
前端使用 `getUserMedia` API 采集视频流，定时截帧（每 30 秒）。后端新增图片上传接口，调用大模型视觉 API 分析关键帧，写入 student_activities 表，触发 AlertService.analyzeAndUpdate()。

**Step 7 – 教师端预警管理页面**
新建 `views/AlertManagement.tsx`，展示学生预警列表和等级分布图。后端补充 AlertService.getAlertStatistics() 真实分级统计。

**Step 8 – 考核题目与教学讲义生成**
文件解析完成后，增加"生成题目/讲义"按钮，调用现有 AiIntelligenceService.chat() 方法实现。

### 第四阶段（最终打磨）

- 外部资讯接入（NewsAPI / arXiv）
- 知识图谱历史批量导入管理
- 定时任务配置界面（控制爬虫间隔）
- 学生学习报告导出（PDF）

---

## 五、技术债与注意事项

| 问题 | 代码位置 | 建议处理方式 |
|------|---------|-------------|
| parseDocument() 使用模拟数据 | AiIntelligenceService.java:331 | 接入真实 MinerU API，配置有效 Key |
| AlertService.getAlertStatistics() 未按等级统计 | AlertService.java:154 | 补充按 alert_level 分组统计的 SQL |
| callGemini() 回退到 OpenAI | AiIntelligenceService.java:218 | 若需接入 Gemini，实现正式调用方法 |
| 爬虫规则仅有 2 个站点 | crawler/rule/impl/ | 可增加新华网、科技日报等规则 |
| 需要 HTTPS 才能访问摄像头 | 前端整体 | 开发时使用 localhost，部署时配置 SSL |
| Token 存储在 localStorage | services/api.ts:17 | 生产环境迁移到 HttpOnly Cookie |

---

## 六、四大子系统完成率总览

| 子系统 | 估计完成度 | 主要缺口 |
|--------|-----------|---------|
| 子系统 1：思政价值挖掘 | **92%** | 多模型选择 UI ✅已完成、定时任务配置界面 |
| 子系统 2：课程知识图谱 | **85%** | Excel 导入建图 ✅已完成、学习路径前端展示 ✅已完成 |
| 子系统 3：智能教学内容生成 | **70%** | AI 结构化解析 ✅已完成、讲义/题目生成 UI、外部资讯接入 |
| 子系统 4：学生监测与预警 | **55%** | 学生首页 ✅已完成、摄像头采集、情绪分析、预警管理界面 |
| **整体完成度** | **约 76%** | — |
