# AI Scene Route Policy

## 结论

已完成 P0 多模型按业务场景路由的后端最小闭环，覆盖聊天、文档解析、思政匹配、题目生成、资源抓取和学习路径建议；该项在验收清单中由未实现更新为部分实现。

## 改动原因

- `/Draft/毕设.md` 要求系统具备 AI 助手、多模型能力、文档解析与教学内容生成闭环。
- `pending-items-technical-plan` 将“按业务场景区分模型用途”列为 P0。

## 具体改动

- `AiIntelligenceService` 新增 `TASK_*` 场景常量、`chatForTask` 入口和 `ai.routes.*` 路由解析。
- 结构化解析流水线按 `parse / ideology / question-gen` 调用不同 provider 链。
- `ResourceCrawlService` 和 `PathRecommendService` 分别切到 `crawl / path` 路由。
- `application.yml` 新增 `AI_ROUTE_CHAT/PARSE/IDEOLOGY/QUESTION_GEN/CRAWL/PATH` 环境变量，并移除 proxy api-key 疑似真实默认值。
- `AiIntelligenceServiceTest` 补充场景路由链、未知场景回退和 Gemini 未实现显式失败测试。
- 更新 `2026-04-16-1337-acceptance-checklist.md` 对应状态与维护记录。

## 方案取舍

- 采用配置化 provider 链而非新增模型配置页面，保证当前 P0 可闭环且不扩展前端范围。
- 继续复用既有 `background-chain` 作为回退，避免破坏现有 provider 熔断与降级逻辑。

## 风险与注意事项

- 视觉分析仍未接入，不可标记为完成。
- Gemini 当前为预留 provider，若被配置进路由会显式失败并进入后续 fallback，而不会伪装成其他 provider。
- 仍需真实多 provider 联调；当前测试只验证路由策略，不触发外部 API。
- 配置模板和部署说明仍是后续任务。

## 验证情况

- `mvn -f backend/pom.xml -Dtest=AiIntelligenceServiceTest test` 通过，10 个用例全部成功。
- `mvn -f backend/pom.xml -DskipTests compile` 通过。
- 代码评审首次提出的疑似真实 key 与 Gemini fallback 语义问题已修复。

## 下一位 agent 的接手提示

- 下一优先项建议继续处理配置模板与部署说明，或接入视觉/预警闭环。
- 重点文件：`AiIntelligenceService.java`、`application.yml`、验收清单第八章。