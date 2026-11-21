# Sprint 1 本地一键回归交付

## 结论

我已完成 `scripts/verify_project.ps1`。脚本按顺序执行后端测试、前端类型检查和前端构建；任一步骤失败时立即停止并返回非零退出码。本次改动属于 `/Draft/毕设.md` 的基础质量与维护能力补充，不改变业务功能、接口、配置或数据结构。

## TODO 完成情况

- [x] 核对后端测试、前端类型检查和构建命令
- [x] 完成本地一键回归脚本
- [x] 验证失败后立即停止并返回非零退出码
- [x] 验证完整成功路径并记录结果
- [x] 更新功能验收清单维护记录
- [ ] PR 由另一名组员完成 review

## 具体改动

- 新增 `scripts/verify_project.ps1`。
- 脚本依次运行 `mvn -q -f backend/pom.xml test`、`tsc --project tsconfig.json --noEmit` 和 `npm run build`。
- 缺少 Maven、TypeScript 本地命令或 npm 时给出明确错误并退出。
- 仅新增回归入口和交付记录，没有修改业务代码，也没有新增依赖。

## 运行记录

### 失败路径

- 时间：2026-09-26 11:54（UTC+8）
- 命令：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_project.ps1`
- 环境：默认 Java 17，项目测试类要求 Java 25。
- 结果：后端测试返回退出码 1；脚本输出 `backend tests failed (exit code 1).` 后立即停止，未执行前端类型检查和构建。
- 判定：失败传播和快速停止行为符合要求。

### 成功路径

- 时间：2026-09-26 11:56（UTC+8）
- 前提：当前 PowerShell 会话的 `JAVA_HOME` 指向 Temurin JDK 25.0.4.1。
- 命令：`.\scripts\verify_project.ps1`
- 后端：34 个测试套件、178 个测试全部通过，0 failure、0 error、0 skipped。
- 前端类型检查：通过。
- 前端构建：Vite 6.4.1 构建通过，共转换 3329 个模块。
- 最终输出：`Project verification passed.`
- 退出码：0。

## 风险与注意事项

- 后端 `pom.xml` 目标版本为 Java 25；运行前必须让 `JAVA_HOME` 和 `PATH` 指向 JDK 25，否则脚本会在后端阶段按预期失败。
- 本次未修改或清理工作区已有的测试产物和其他未提交文件；提交时只应暂存本任务文件。

## 后续接手提示

- 在 PR 页面指定一名组员 review，并保留本文件作为成功、失败运行证据。
- 后续若项目调整 Java 版本，应同步更新 `backend/pom.xml` 与本记录中的环境说明。
