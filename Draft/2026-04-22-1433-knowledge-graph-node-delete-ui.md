# Knowledge Graph Node Delete UI

## 结论

已补齐知识图谱节点删除的前端入口。教师/管理员现在可以在节点详情抽屉中看到删除按钮，确认后调用既有删除接口并刷新图谱；学生端不会显示该入口。

## 改动原因

- 当前任务是完善知识图谱删除节点功能，属于 `/Draft/毕设.md` 中“知识图谱更新与管理功能”的直接实现。
- 后端已有删除接口和删除逻辑，但前端没有按钮和调用链路，导致功能实际不可达。

## 具体改动

- 修改 `views/KnowledgeGraph.tsx`：在节点详情抽屉中新增删除按钮和二次确认，删除成功后关闭抽屉并刷新图谱；入口受 `canEditKnowledgeGraph` 能力位控制。
- 修改 `services/api.ts`：补充 `knowledgeApi.deleteNode` 请求方法。
- 修改 `backend/src/test/java/com/smartedu/controller/KnowledgeControllerTest.java`：新增删除节点接口回归测试。
- 未新增或删除业务文件，未改动数据库、接口路径和现有删除主逻辑。

## 方案取舍

- 采用最小改动方案，复用现有 `DELETE /api/knowledge/nodes/{id}`，只补前端缺失的可达性和测试。
- 没有顺手重构知识图谱权限、关系编辑或 service 删除逻辑，避免把本次任务扩散成整块图谱编辑改造。

## 风险与注意事项

- 当前仅对删除按钮做了前端能力位控制，未顺手扩展到关系创建等其他既有编辑动作。
- 思政节点继续视为非删除对象，前端不会显示删除按钮。
- 浏览器运行态交互尚未手动点击确认。

## 验证情况

- 已执行 `npm run build`，通过。
- 已执行 `mvn -q test`，通过。
- 未执行真实登录态下的浏览器手动删除验收。

## 下一位 agent 的接手提示

- 若继续完善知识图谱编辑闭环，优先看 `views/KnowledgeGraph.tsx` 和 `services/api.ts`。
- 当前最接近的后续缺口是关系删除/编辑、重复关系提示，以及教师/学生图谱编辑能力的统一收口。
