# Auth And Crawl Hardening

## 结论

本次做了最小范围健壮性修复：认证接口现在能稳定处理空请求体，资源抓取服务清理了不会改变现有行为的死代码，并补齐了对应回归测试。标题去重策略和 AI provider chain 测试性等更大范围问题本轮未改。

## 改动原因

- 上轮审阅确认 `AuthController` 在空 body 下存在不稳健点。
- `ResourceCrawlService` 中存在 fallback/tag 的冗余与不可达分支，影响维护判断。
- 本次目标是先消除已确认缺陷，不扩大到业务语义调整或架构重构。

## 具体改动

- `backend/src/main/java/com/smartedu/controller/AuthController.java`
  - `login/register` 改为 `@RequestBody(required = false)`，并增加 `request == null` 判空，返回业务级 bad request。
- `backend/src/main/java/com/smartedu/service/ResourceCrawlService.java`
  - 删除 fallback 中先构造默认 tags 再被覆盖的冗余代码。
  - 删除 `buildTagsJson` 中的 `if (false && ...)` 和重复的空集合分支。
- `backend/src/test/java/com/smartedu/controller/AuthControllerTest.java`
  - 新增登录/注册空 body 回归测试。
- `backend/src/test/java/com/smartedu/service/ResourceCrawlServiceTest.java`
  - 新增空标签数组仍返回 `[]` 的回归测试。
- `backend/src/test/java/com/smartedu/controller/UploadControllerTest.java`
  - 补齐 `AiIntelligenceService` 测试桩构造参数，使定向测试可正常编译运行。

## 方案取舍

- 沿用现有 controller 的方法内判空 + `Result.badRequest(...)` 模式，没有引入 `@Valid` 或全局异常处理，以保持最小改动。
- 保持资源抓取 fallback 返回空标签数组 `[]` 的现有语义，不借这次清理顺手改业务规则。
- 未调整标题去重策略，避免影响资源创建行为。

## 风险与注意事项

- 标题去重是否过于激进仍是已知设计风险，本轮未处理。
- AI provider chain 的公开行为测试性问题仍保留，后续若要补强需先加测试 seam。
- 本次只做后端最小修复，未做浏览器端手工回归。

## 验证情况

- 已执行：`mvn -f backend/pom.xml -Dtest=AuthControllerTest,ResourceCrawlServiceTest test`
- 结果：通过。
- 额外说明：测试编译阶段暴露 `UploadControllerTest` 的 `AiIntelligenceService` 构造参数落后于生产代码，已一并补齐测试桩后重新验证通过。

## 下一位 agent 的接手提示

- 若继续处理上轮审阅遗留项，优先看：
  - `backend/src/main/java/com/smartedu/service/ResourceCrawlService.java`
  - `backend/src/main/java/com/smartedu/service/AiIntelligenceService.java`
- 下一步更适合单独推进：标题去重策略复核、AI provider chain 公开行为测试。