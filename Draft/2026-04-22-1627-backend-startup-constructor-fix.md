# Backend Startup Constructor Fix

## 结论

已修复导致本地后端启动链路不稳定的问题：`KnowledgeService` 由 Lombok 生成构造器改为显式构造器。修复后，`mvn test-compile` 通过，`/api/knowledge/graph`、`/api/dashboard/stats`、`/api/resources/crawl/status` 返回 200，可支撑知识图谱页面正常取数。

## 改动原因

- 前端报 `:3000/api/*` 500，属于代理到后端失败后的统一表现。
- 排查发现 `KnowledgeService` 源码依赖是 9 项，但编译产物存在构造器签名不一致风险，影响本地启动与测试编译稳定性。
- 该修复属于实现 `/Draft/毕设.md` 目标过程中的基础稳定性缺陷修复。

## 具体改动

- 修改文件：`backend/src/main/java/com/smartedu/service/KnowledgeService.java`
- 改动内容：
  - 移除 `@RequiredArgsConstructor`
  - 新增显式 9 参数构造器并完成字段赋值
- 未新增/删除文件；未修改接口协议、数据库结构、业务字段语义。

## 方案取舍

- 采用显式构造器是最小且确定性的修复，直接消除构造签名漂移风险。
- 未做无关重构（如改动控制器、前端请求层、代理配置），避免扩大影响面。

## 风险与注意事项

- 该修复不改变业务逻辑，只修复构建/启动一致性。
- 若本地仍出现前端 500，优先检查后端 `8080` 是否在运行。

## 验证情况

- `mvn -q -DskipTests compile`：通过
- `mvn -q test-compile`：通过
- `GET http://localhost:8080/api/knowledge/graph`：200
- `GET http://localhost:8080/api/dashboard/stats`：200
- `GET http://localhost:8080/api/resources/crawl/status`：200

## 下一位 agent 的接手提示

- 先看：`backend/src/main/java/com/smartedu/service/KnowledgeService.java`
- 若出现 `:3000/api/*` 500，先验证后端端口监听与上述三个接口状态，再决定是否排查前端代理层。
