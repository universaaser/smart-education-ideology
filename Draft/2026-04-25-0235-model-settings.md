# Model Settings

## 结论

完成 PRD 第 7 包 7.1-7.6 的 demo 闭环：管理员可在 `Model Settings` 查看并保存 AI providers 与场景 routes，密钥不回显，只显示是否已配置。

## 改动原因

- 对应 `/Draft/毕设.md` 的多模型能力与场景路由展示需求。
- 对应 `Draft/PRD.md` 第 7 包，让答辩中可见 provider、模型名、API base、启用状态和任务路由。

## 具体改动

- 新增 `ai_provider_configs`、`ai_route_configs` 主 schema 与迁移脚本，以及对应 entity/mapper/DTO。
- 新增 `/api/admin/ai-providers` provider 列表、保存、配置完整性测试接口；新增 routes 列表和保存接口。
- 新增管理员 `MODEL_SETTINGS` 视图、能力位、侧边栏菜单、`TeacherShell` 懒加载入口和 `views/ModelSettings.tsx`。
- 新增 `AiProviderConfigControllerTest` 覆盖 provider/route 成功和非法输入路径。

## 方案取舍

- 第一版不改 `AiIntelligenceService` 运行时 DB 读取，避免破坏当前本地 OpenAI 兼容主链路。
- `testProvider` 当前是配置完整性检查，不发真实外部请求；页面已提示 demo 生效边界。

## 风险与注意事项

- DB 配置已持久化，但尚未热切到所有 AI 服务。
- 密钥以 demo 方式存库，未做高级密钥管理。
- 仍需真实数据库执行迁移、浏览器手动验收、接口级权限验证、真实 provider 联调和部署配置模板。

## 验证情况

- `mvn -f backend/pom.xml -Dtest=AiProviderConfigControllerTest test`：7 tests，0 failures，BUILD SUCCESS。
- `npm run build`：通过，生成 `ModelSettings` 懒加载 bundle。

## 下一位 agent 的接手提示

- 下一包按 PRD 顺序是第 8 包“管理员控制台：用户、课程、学生绑定”。
- 如继续完善第 7 包，优先接 `AiIntelligenceService` 运行时 DB 路由或补真实 provider 最小连接测试。
