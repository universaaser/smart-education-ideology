# Smart Education Ideology

面向“课程知识图谱 + 思政知识库 + 文档解析 + 教学内容生成 + 学生学习支持”的本地演示系统。前端使用 React/Vite，后端使用 Spring Boot/MyBatis-Plus，数据库使用 MySQL。

## 环境要求

- Node.js 18+，npm 9+
- JDK 17+
- Maven 3.8+
- MySQL 8+
- 可选：本地 OpenAI-compatible 服务、MinerU、Qdrant

## 本地启动

以下命令按 Windows PowerShell 编写，默认在项目根目录执行。

### 1. 初始化数据库

首次启动建议创建空库后导入主 schema：

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS smart_education DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
Get-Content -Raw backend/src/main/resources/schema.sql | mysql -u root -p smart_education
```

如果是已有数据库，不要重复导入完整 `schema.sql` 覆盖环境；只按缺失功能执行对应迁移脚本。当前演示闭环涉及的迁移脚本包括：

- `backend/src/main/resources/migration_subject_ideology_split.sql`
- `backend/src/main/resources/migration_selection_explain_records.sql`
- `backend/src/main/resources/migration_light_rag_chunks.sql`
- `backend/src/main/resources/migration_teaching_materials.sql`
- `backend/src/main/resources/migration_parse_tasks_longtext.sql`
- `backend/src/main/resources/migration_parse_tasks_error_detail.sql`
- `backend/src/main/resources/migration_json_type_upgrade.sql`
- `backend/src/main/resources/migration_parse_task_corrections.sql`
- `backend/src/main/resources/migration_phase2_projection_tables.sql`
- `backend/src/main/resources/migration_material_trace_and_course_binding.sql`
- `backend/src/main/resources/migration_course_material_rules.sql`
- `backend/src/main/resources/migration_student_activity_events.sql`
- `backend/src/main/resources/migration_student_alert_records.sql`
- `backend/src/main/resources/migration_course_chapters.sql`
- `backend/src/main/resources/migration_crawl_sources_review.sql`
- `backend/src/main/resources/migration_keyword_tasks.sql`
- `backend/src/main/resources/migration_match_reviews.sql`
- `backend/src/main/resources/migration_ai_provider_configs.sql`
- `backend/src/main/resources/migration_course_students.sql`
- `backend/src/main/resources/migration_knowledge_change_logs.sql`

单个迁移可这样执行：

```powershell
Get-Content -Raw backend/src/main/resources/migration_knowledge_change_logs.sql | mysql -u root -p smart_education
```

已有数据库长期未更新时，推荐先用脚本做 dry-run，再执行自动升级。脚本会先备份数据库，创建本地 `schema_migrations` 记录表，并通过 `information_schema` 判断已存在的表、字段和索引；已存在的迁移会登记为 adopted，缺失的才按顺序执行。

```powershell
.\scripts\update_database.ps1 -User root -Password root -DryRun
.\scripts\update_database.ps1 -User root -Password root
```

如果 `mysql` 或 `mysqldump` 不在 PATH 中，显式传入 MySQL bin 路径：

```powershell
.\scripts\update_database.ps1 `
  -MysqlPath "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" `
  -MysqldumpPath "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" `
  -User root `
  -Password root
```

如果数据库尚不存在，可用 `-InitIfMissing` 从最新版 `schema.sql` 初始化；已有库不要使用该参数替代备份和迁移检查。

### 2. 配置后端

仓库提供 `.env.example` 作为本地配置模板。需要覆盖默认配置时，先复制一份本地文件，再按机器环境修改；不要提交真实密钥或生产密码：

```powershell
Copy-Item .env.example .env.local
```

Vite 会读取 `.env.local` 中的前端变量；Spring Boot 运行时建议在 PowerShell 中设置对应环境变量，或继续使用 `application.yml` 的本地默认值。

默认配置位于 `backend/src/main/resources/application.yml`：

- 后端端口：`8080`
- 数据库：`jdbc:mysql://localhost:3306/smart_education`
- 默认数据库账号：`root / root`
- 上传目录：`./uploads`
- 允许上传类型：`pdf,doc,docx,ppt,pptx,xls,xlsx,md,markdown`

如本机数据库密码不是 `root`，优先修改本地运行环境或临时覆盖配置，不要把真实密码提交到仓库。

### 3. 配置 AI、MinerU 与向量检索

当前后端默认走本地 OpenAI-compatible 接口：

```powershell
$env:OPENAI_ENABLED="true"
$env:OPENAI_API_BASE="http://localhost:8317/v1"
$env:OPENAI_MODEL="gpt-5.4"
$env:OPENAI_API_KEY="your-local-key"
```

MinerU 默认启用，但未配置 `MINERU_API_KEY` 时会回退到本地文档抽取链路：

```powershell
$env:MINERU_ENABLED="true"
$env:MINERU_API_KEY="your-mineru-key"
```

Qdrant 默认关闭；只有需要向量语义检索时再启用：

```powershell
$env:QDRANT_ENABLED="true"
$env:QDRANT_HOST="localhost"
$env:QDRANT_PORT="6333"
```

### 4. 启动后端

```powershell
mvn -f backend/pom.xml spring-boot:run
```

常用后端验证命令：

```powershell
mvn -f backend/pom.xml test
```

### 5. 启动前端

```powershell
npm install
npm run dev
```

Vite 默认开发端口为 `5173`。生产构建与本地预览：

```powershell
npm run build
npm run preview
```

## 演示账号与数据准备

仓库当前没有可靠的固定演示账号 seed，因此不要假设存在固定密码。

推荐准备方式：

1. 打开前端登录页，切到 `Register`。
2. 注册一个 `Teacher` 账号，用于教师主线演示。
3. 注册一个 `Student` 账号，用于学生主线演示。
4. 管理员账号推荐由已有管理员在 `Admin Console` 创建；如果本地空库没有管理员，可先注册一个教师账号，再仅在本地演示库中将该用户角色改为 `ADMIN`：

```powershell
mysql -u root -p smart_education -e "UPDATE users SET role='ADMIN' WHERE username='your_teacher_username';"
```

5. 使用管理员账号进入 `Admin Console`，将学生绑定到要演示的课程，否则学生端 `Course Library` 可能没有课程数据。
6. 使用教师账号上传课程文档或导入知识图谱 Excel，生成课程材料、知识点、思政匹配和资源库数据。

## 已实现页面入口

教师/管理员 Shell 中当前可演示页面：

- `Dashboard`
- `Resource Upload`
- `Course Library`
- `Knowledge Graph`
- `AI Assistant`
- `Student Alerts`
- `Source Management`
- `Keyword Tasks`
- `Match Review`
- `Model Settings`
- `Admin Console`

学生 Shell 中当前可演示页面：

- `Student Home`
- `Course Library`
- `Knowledge Graph`
- `AI Assistant`

## 建议演示顺序

完整脚本见 `Draft/2026-04-25-0305-demo-script.md`，测试用例表见 `Draft/2026-04-25-0305-test-cases.md`。

建议答辩演示顺序：

1. 管理员创建/启停用户，绑定学生课程，检查模型配置。
2. 教师上传课程文档，解析并生成教学材料。
3. 教师在资源库查看材料版本、预览和 Markdown 导出。
4. 教师在知识图谱查看节点、编辑/删除/撤销关系。
5. 教师配置知识源、运行关键词任务并审核专业知识-思政匹配。
6. 学生查看学习报告、打开课程资源、查看图谱节点并向 AI 提问。
7. 教师生成学生预警并处理反馈。

## 常见问题

### AI 回复为空或失败

确认本地 OpenAI-compatible 服务已启动，并检查：

```powershell
$env:OPENAI_API_BASE
$env:OPENAI_MODEL
$env:OPENAI_API_KEY
```

当前默认模型名是 `gpt-5.4`，如本地服务不支持该名称，需要改为本地服务实际支持的模型。

### 文档解析没有 MinerU 结构化结果

确认 `MINERU_API_KEY` 是否配置。未配置或外部服务失败时，系统会走本地 fallback 抽取，演示时应说明这是降级结果。

### 学生看不到课程资源

确认管理员已在 `Admin Console` 将该学生绑定到对应课程。学生端资源库按绑定课程过滤。

### 知识图谱关系无法编辑

只有真实 `knowledge_relations` 关系支持编辑、删除和撤销；由专业知识-思政匹配合成的负 id 关系是只读关系。

### 抓取或关键词任务没有外部结果

外部站点可用性、网络和 AI 摘要服务都会影响结果。演示时可先准备已审核资源，再运行关键词任务，以保证可展示结果。

### Qdrant 语义检索没有命中

默认 `QDRANT_ENABLED=false`。如果不启用向量检索，聊天和检索会回到轻量 RAG/fallback 路径。

### 需要生产部署吗

当前 README 只覆盖本地演示启动。生产部署仍需补 HTTPS、反向代理、日志目录、文件安全、token 密钥、接口权限和数据库备份策略。
