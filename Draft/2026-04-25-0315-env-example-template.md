# Env Example Template

## 结论

补齐本地演示所需 `.env.example`，并在 README 中说明复制到 `.env.local` 与后端环境变量覆盖方式。验收清单中 `.env.example` 配置模板条目已从未完成更新为完成。

## 改动原因

- 对应验收清单“数据、配置与部署”中 `.env.example` 或配置模板缺口。
- 支撑 README 第 10 包交付，避免后续 agent 或答辩者从 `application.yml` 中手工拼接环境变量。

## 具体改动

- 新增 `.env.example`：覆盖 Vite/Gemini、后端端口、MySQL、JWT、AI route、OpenAI-compatible 默认运行时、DeepSeek/Gemini/proxy 占位、pipeline limits、embedding、Qdrant、MinerU 与上传目录。
- 更新 `README.md`：补充复制 `.env.example` 到 `.env.local` 的本地使用说明。
- 更新验收清单与 `Draft/CHANGE-INDEX.md`。

## 方案取舍

- 不读取或复制本地 `.env.local`，避免泄露真实密钥。
- 模板使用占位符、本地 demo 默认值或 `example.invalid`，不写真实生产 endpoint 或 key。
- 不改后端配置加载逻辑；Spring Boot 仍通过现有 `${ENV:default}` 机制读取环境变量。

## 风险与注意事项

- `.env.local` 被 `.gitignore` 忽略，真实密钥应只放本地。
- `SERVER_PORT`、`SPRING_DATASOURCE_*` 等 Spring Boot 标准变量可覆盖常规配置；若后续改用专用变量，需要同步模板。
- 生产部署安全、HTTPS、反向代理、日志目录、token 密钥轮换仍未完成。

## 验证情况

- 文档审查：未写入真实密钥；`.env.example` 只含占位符和本地默认值。
- `npm run build`：通过。
- 未执行后端测试：本轮未修改后端代码。

## 下一位 agent 的接手提示

若继续推进部署相关验收项，建议补独立生产部署说明，覆盖 HTTPS/反向代理、日志目录、文件上传安全、JWT secret、数据库备份和接口级权限策略。
