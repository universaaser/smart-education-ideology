# PRD Implementation Review

## 结论

本次按 `Draft/PRD.md`、`/Draft/毕设.md` 和验收清单审查功能实现与代码质量。PRD 中标为必做的 demo 闭环大多已有页面、接口和测试证据，但仍不能视为正式全量验收：真实浏览器回归、接口级权限、真实 AI/provider 联调、视觉采集、答题正确率、定时调度和生产安全仍未补齐。本次直接修复了低风险健壮性问题，并未扩大功能边界。

## 改动原因

- 管理员用户创建/更新对角色输入过于宽松，未知角色会被后续归一化逻辑静默落到默认角色，容易造成误配置。
- 预警列表和状态更新对状态、等级参数缺少统一入口校验，非法值可能静默查空或延迟到 service 才失败。
- 前端开发环境 mock bootstrap 使用 `as any` 掩盖权限字段缺口，且教师 mock 权限与后端 `RoleAccessService` 不一致。
- 以上均对应 `/Draft/毕设.md` 中基础平台、学生预警和教师端闭环的稳定运行要求。

## 具体改动

- `backend/src/main/java/com/smartedu/controller/AdminController.java`：新增支持角色白名单，创建/更新用户时拒绝非法角色；更新密码时补齐最小长度校验。
- `backend/src/main/java/com/smartedu/controller/AlertController.java`：新增预警状态白名单，列表筛选允许空状态但拒绝非法状态，状态更新统一大小写归一并校验；补充预警等级 1-3 校验。
- `contexts/AuthContext.tsx`：移除 mock 权限对象的 `as any`，改用 `Role`/`View` 枚举和 `satisfies`；让教师 mock 入口与后端教师权限保持一致。
- `backend/src/test/java/com/smartedu/controller/AdminControllerTest.java`、`backend/src/test/java/com/smartedu/controller/AlertControllerTest.java`：补非法角色、短密码、非法预警筛选、非法/小写状态更新回归。
- 未新增依赖，未修改数据库结构，未调整 PRD 验收状态。

## 方案取舍

- 采用控制器入口校验而非重做角色/状态枚举体系，是因为当前接口已成形，入口收口能用最小改动避免脏数据和误操作。
- 保留现有 DTO、service 和返回格式，避免影响前端已接入页面。
- mock 权限只对齐后端现状，不新增真实权限体系；接口级权限仍按验收清单继续标为未完成。

## 风险与注意事项

- `AdminController`、`AlertController` 等 PRD 扩展文件在当前工作树仍是未跟踪状态，说明相关功能尚未进入干净基线；后续提交前需整体审查这些新增文件。
- PRD 剩余未完成项包括答题正确率、摄像头/视觉分析、章节-知识点绑定、抓取定时调度、外部资讯 API、一键回滚、多 provider 全链路联调、班级组织、节点编辑增强和 Playwright 自动化。
- 本次未做真实浏览器手动验收，也未连接真实 AI/provider、摄像头或外部资讯源。

## 验证情况

- 已执行 `mvn -q "-Dtest=AdminControllerTest,AlertControllerTest,RoleAccessServiceTest,StudentActivityEventServiceTest" test`，通过。
- 已执行 `mvn -q test`，通过。
- 已执行 `npm run build`，通过。

## 下一位 agent 的接手提示

- 优先补接口级权限和越权测试，重点路径为 admin、model settings、crawl source、keyword task、match review、alert。
- 若继续按 PRD 收口，先做真实浏览器手动验收或 Playwright smoke，再处理视觉/答题/定时调度等仍为 `[TODO]` 的功能点。
- 提交前请注意当前工作树有大量既有未跟踪文件和编译产物，不要误还原或误纳入无关变更。
