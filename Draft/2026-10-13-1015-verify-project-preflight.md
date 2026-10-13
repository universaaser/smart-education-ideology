# 本地回归入口第二次推进：依赖预检查

## 结论与原因

仅推进赵俊楠的 `zane` / [PR #1](https://github.com/universaaser/smart-education-ideology/pull/1)。首次实现为 9/26 提交 `c6b42f4`；本次追加提交，保留两次真实开发记录，不改写历史、不合并 PR。

旧脚本在缺少 TypeScript 时仍先执行后端测试，浪费验证时间。本次属于 `/Draft/毕设.md` 的基础质量支持，将 Maven、TypeScript、npm 检查全部前置；依赖齐全后仍按后端测试、类型检查、构建顺序执行，失败返回 1。

## 改动与取舍

- `scripts/verify_project.ps1`：只移动两条阶段调用，补充一条英文说明，兼容 Windows PowerShell 5 的无 BOM 脚本读取；无新依赖、开关或业务改动。
- 新增本文，同步验收清单条目说明及维护记录；既有 `[x]` 状态不变，PR 仍待其他组员 review。
- 按用户 10/13 的明确要求同步推进，优先于原计划维护组 11/10 开发安排；未调整其他成员任务、分支或课程截止日期。

## 验证

运行前临时将 `JAVA_HOME` / `PATH` 指向现有 Temurin 25.0.4.1，仅影响当前进程。

命令：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_project.ps1`。

- 改前复现：临时目录复制旧脚本，提供 Maven/npm 命令桩但不提供 TypeScript；后端桩执行后才退出 1。
- 改后七条隔离用例通过：缺 Maven、TypeScript、npm 各退出 1，且无阶段执行；后端、类型检查、构建分别返回 9 时脚本立即退出 1；正常桩按三个阶段顺序执行并退出 0。
- 真实运行：34 套件、178 个后端测试，0 失败/错误/跳过；TypeScript 通过；Vite 构建通过，3329 个模块，8.28 秒；完整脚本退出 0。
- Windows PowerShell 5 解析核对三个 `Invoke-Step` 调用存在；`git diff --check` 通过。

## 风险与接手

命令桩仅验证流程控制，真实回归不等于真实数据库/API/浏览器联调；既有 JDK/Maven 警告未处理。修复期间首轮运行跳过后端，未计为完整成功，上述结果均来自修正后重跑。

Git 的仓库代理 10808 不可用；本次临时指定 7890 和 HTTP/1.1 完成读取与 push dry-run，不改持久配置。只显式推送 `zane`，不混入既有 `backend/target`、未提交计划或其他文档。

下一步：在 PR #1 复核本次提交，安排另一名组员 review；保留 9/26 记录，不将作者自查计作他人审核。
