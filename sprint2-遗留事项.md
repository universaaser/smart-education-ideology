# Sprint 2 遗留事项

记录 Sprint 2 期间未能合入实现的两个问题：文件上传校验、跨平台启动文档。两者均已在仓库中留痕，本文档同步归档其上下文、验收标准与后续安排。

---

## 1. 上传大小边界与文件类型头校验 —— 未找到实现 PR

**负责人**：待认领（Sprint 3 纳入）

**状态**：`incomplete`，未找到实现 PR，未合入任何分支。

### 1.1 背景

Resource Upload 入口当前仅按扩展名白名单（`pdf,doc,docx,ppt,pptx,xls,xlsx,md,markdown`，见 `application.yml` 的 `upload.allowed-types`）过滤，存在两项缺口：

- **缺少大小边界校验**：仓库未声明单文件上限，也没有在接口层对请求体大小做统一限制。超大全文件依赖容器或本地磁盘兜底，超限后的行为（截断、413、落盘后清理）未定义。
- **仅校验扩展名，未校验内容头**：扩展名与 `Content-Type` 均由客户端控制，可随意伪造。例如把可执行脚本重命名为 `lecture.pdf`、或将 PDF 扩展名贴到非 PDF 字节流上，都会被当前逻辑放过。

上传区是外部字节进入系统的主要入口，这两项是风险集中点，也是答辩演示中容易被追问的环节。

### 1.2 验收标准

实现须覆盖以下四点，且每类都要有对应的自动化测试，不能只做手工演示：

1. **合法文件**：字节头与扩展名一致、且在允许类型白名单内，上传成功，落盘到 `uploads/` 并生成解析任务。
2. **空文件**：0 字节文件必须被拒绝，返回明确的业务错误（如 `FILE_EMPTY`），**不产生**任何落盘文件。
3. **超限文件**：超过约定上限的文件必须被拒绝。上限需要在配置中显式声明（建议单文件默认 100 MB，可按环境覆盖），拒绝发生在读盘落盘之前；边界值需要同时覆盖「上限 + 1 字节」与「上限本身」两例。
4. **伪装类型**：扩展名或 `Content-Type` 声明为允许类型、但实际字节头不匹配（例如 `fake.pdf` 实为 PE/脚本、或 PNG 改名为 `.md`），必须被拒绝，且**不产生**任何落盘文件。

### 1.3 测试矩阵

| 用例 | 输入 | 预期结果 | 上传目录新增文件 |
|---|---|---|---|
| 合法 PDF | 正确 `%PDF-` 头 + `.pdf` | 200，落盘并建任务 | 1 个 |
| 合法 DOCX | 正确 ZIP 头 + 含 `word/document.xml` + `.docx` | 200，落盘 | 1 个 |
| 合法 Markdown | `.md`，正文内容 | 200，落盘 | 1 个 |
| 空文件 | 0 字节 + `.pdf` | 4xx，`FILE_EMPTY` | **0** |
| 上限边界（超限 1 字节） | 上限 + 1 字节的 PDF | 4xx，文件过大 | **0** |
| 上限边界（等于上限） | 正好等于上限的 PDF | 200，落盘 | 1 个 |
| 伪装：脚本改扩展名 | `lecture.pdf`，实际为脚本字节 | 4xx，类型不匹配 | **0** |
| 伪装：PNG 改名 | `note.md`，实际为 PNG 头 | 4xx，类型不匹配 | **0** |
| 不允许的类型 | `.exe` 或正确 PE 头 | 4xx，类型不在白名单 | **0** |

**关键断言**：每个「拒绝」用例之后，必须对上传目录做基线比对——拒绝前的文件清单与拒绝后完全一致。仅断言 HTTP 状态码为 4xx 是不够的，因为服务端可能在返回错误前已经把字节写入临时文件。

建议实现方式：

- 服务端读取文件头若干字节（PDF 为 `%PDF-`、Office 系为 ZIP 头且内部含对应部件、Markdown 按纯文本兜底），与扩展名做交叉判定；校验通过前不落盘。
- 大小上限在 Controller 层前置校验，并配合 Spring 的 `spring.servlet.multipart.max-file-size` / `max-request-size` 兜底，避免超大请求先占满内存。
- 测试用临时上传目录（`@TempDir`），每个用例执行前后对目录做快照比对；合法用例核对落盘内容，拒绝用例核对目录为空或仅含基线文件。

### 1.4 遗留原因与后续

Sprint 2 内未完成实现 PR，本轮仅完成验收标准与测试矩阵的定义，代码未改动。该条进入 Sprint 3 待办，认领后需要补：

- 校验逻辑与配置项；
- 上述 9 条用例（含上传目录无新增文件的断言）；
- 回归脚本 `verify_project.ps1` 的测试覆盖核对，确保 `mvn test` 能真实跑出这些用例。

---

## 2. 跨平台启动文档 —— 已开 Issue #3 另行跟踪

**负责人**：Wangwenbo（报告人）

**状态**：`tracked separately`，不计入 Sprint 2 完成项。

### 2.1 背景

当前 README 的全部命令示例均按 Windows PowerShell 编写，例如 `Get-Content -Raw ... | mysql`、`Copy-Item`、`.\scripts\update_database.ps1`，以及一键回归脚本 `scripts/verify_project.ps1`。

对非 Windows 开发者的实际影响：

- macOS / Linux 用户无法复制粘贴直接执行，需要逐条手动翻译命令；
- 命令中的 PowerShell 特有语义（如 `Get-Content -Raw` 的换行处理、`.\` 路径写法）翻译出错会导致数据库初始化或脚本执行失败；
- 协作团队与 CI 环境的 onboarding 成本偏高，CI 通常运行在 Linux runner 上。

### 2.2 处理方式

已在仓库开 Issue 跟踪：

> **issue：Startup commands only cover Windows PowerShell, lacking cross-platform support（Wangwenbo）** · Issue #3
> https://github.com/universaaser/smart-education-ideology/issues/3

Issue 中提出的建议方向：

1. 提供等效的 Bash / macOS / Linux 命令；
2. 在 `scripts/` 下补 `.sh` 版本，形成 `scripts/update_database.ps1` 与 `scripts/update_database.sh` 一一对应；
3. 在 README 开头明确声明「所有命令示例基于 Windows PowerShell」，避免误导；
4. 考虑引入 `Makefile` 或 `justfile`，把常用命令封装成统一入口。

### 2.3 与本 Sprint 的边界

该问题**不在本 Sprint 2 的完成项内**，单独跟踪，避免与上传校验的实现工作互相阻塞。后续可在 Issue #3 下直接提交 `.sh` 脚本与 README 改造，合入后再关闭该 Issue。

---

## 收尾

| 编号 | 事项 | Sprint 2 状态 | 跟踪位置 |
|---|---|---|---|
| 1 | 上传大小边界 + 文件头校验 | 未完成，未找到实现 PR | 本文档 §1，Sprint 3 待办 |
| 2 | 跨平台启动文档 | 已开单，另行跟踪 | Issue #3 |

两条均不计入 Sprint 2 完成项，也不影响当前 `Project verification passed.` 的回归结论。
