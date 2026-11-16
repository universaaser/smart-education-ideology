# 一键回归交付与复核记录

## 交付范围

赵俊楠负责的一键回归入口已实现并完成本轮验证，属于《毕设.md》的辅助质量保障能力。`scripts/verify_project.ps1` 串行执行后端测试、前端类型检查和构建；`scripts/test_verify_project.ps1` 提供七条独立的流程测试。收口仅补 README 与验证记录，保持脚本行为、接口、配置和数据库不变。

## 验证（2026-11-16，UTC+8）

从 `8bba274` 导出 Windows 临时副本，用现有前端依赖并清除副本中的旧后端产物后运行。使用 `README.md` 的一键回归命令复跑：

- JDK 25.0.5.0.3、Maven 3.9.9、Node 24.14.0：后端 34 套件、178 个测试通过（0 失败/错误/跳过），TypeScript 通过，Vite 构建通过（3329 个模块），退出 0。
- JDK 17：后端编译因不支持 Java 25 目标失败，脚本退出 1，未执行前端阶段。
- Windows PowerShell 5.1 与 PowerShell 7：`scripts/test_verify_project.ps1` 各七例通过，覆盖三种工具缺失、三个阶段独立失败及正常执行顺序。

## 待办

按 [PR #1](https://github.com/universaaser/smart-education-ideology/pull/1) 的步骤由其他组员独立审查，再安排合并。此次结果针对 `zane` 的脚本与源码，未运行合并版本、真实数据库或浏览器端到端测试。
