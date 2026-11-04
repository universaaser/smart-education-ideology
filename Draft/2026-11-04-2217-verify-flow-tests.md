# 回归流程测试固化与 PR 审查

## 结论

在 `zane` 新增 `scripts/test_verify_project.ps1`，将 10/13 的七条临时验证固化为可复跑测试。生产脚本、业务代码、接口、依赖均未修改；对应 `/Draft/毕设.md` 的质量保障能力。

## 使用与验证

在项目根目录运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_verify_project.ps1
pwsh.exe -NoProfile -File .\scripts\test_verify_project.ps1
```

- 两种 PowerShell 均通过七条用例并退出 0：缺 Maven、TypeScript、npm 时无阶段执行且退出 1；三个阶段分别失败时立即停止；成功时严格按后端、类型检查、构建顺序执行并退出 0。
- 测试仅复制生产脚本到唯一临时目录，使用 `.cmd` 命令桩；子进程隔离 PATH 和退出行为，结束后仅清理本次临时目录。
- 反向验证：在另一个临时目录配合 `c6b42f4` 初版运行，测试正确拒绝“缺 TypeScript 时仍执行后端”的行为，证明能捕获已有回归。

## 范围与风险

这是流程控制测试，不依赖真实 Java/Maven/npm 安装，也不等同于后端、构建或数据库验收。当前仅发现 Java 17/21，之前的临时 JDK 25 已不存在；本次未重跑真实后端测试或生产构建，不沿用 10/13 的 178 测试结果冒充本次结果。测试仍按既有 Windows `.cmd` 环境设计，不新增跨平台承诺。

已同步验收清单说明与维护记录，状态保持 `[x]`；PR #1 仍需其他组员审查。历史提交保留，本次追加一次真实提交。

## PR #11 审查与接手

确认无人审核后，针对 `6a19d52` 发布 [Request changes](https://github.com/universaaser/smart-education-ideology/pull/11#pullrequestreview-5769839710)：补业务改动的验收维护记录、核实纪要的 Issue #5 状态和出席备注；明确服务单测未执行真实 SQL及本次未运行其后端测试。未修改对方分支或合并任何 PR。

下一步：卢琦贺修复审查意见；安排他独立审查 PR #1。只推送 `zane`，不把既有测试产物或其他未提交文档混入提交。
