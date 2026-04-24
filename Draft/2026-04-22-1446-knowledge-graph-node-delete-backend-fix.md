# Knowledge Graph Node Delete Backend Fix

## 结论

已修复知识图谱删除节点时的后端 `500` 风险。删除学科节点前，系统现在会先断开直接依赖记录，再执行节点删除，因此课程绑定、来源追溯、材料追溯和学习记录不会再阻塞删除链路。

## 改动原因

- 本次是对 `/Draft/毕设.md` 中知识图谱管理能力的缺陷修复。
- 前一轮已补前端删除按钮，但运行态删除仍报 `Request failed: 500 Internal Server Error`，说明后端删除链路未清完节点依赖。

## 具体改动

- 修改 `backend/src/main/java/com/smartedu/service/KnowledgeService.java`：
  删除节点前新增课程-知识点绑定、知识点来源记录清理，并将 `teaching_material_traces`、`student_activities` 中的 `knowledge_point_id` 置空后再删除节点。
- 新增 `backend/src/test/java/com/smartedu/service/KnowledgeServiceTest.java`：
  校验删除学科节点时会执行依赖解绑；同时校验思政节点删除请求继续被忽略。
- 修改 `backend/src/test/java/com/smartedu/controller/KnowledgeControllerTest.java`、`backend/src/test/java/com/smartedu/controller/PathRecommendControllerTest.java`：
  适配 `KnowledgeService` 新构造器。

## 方案取舍

- 采用最小且稳妥的方案，在现有删除入口上补依赖解绑，不重做删除接口、不引入新的删除模式。
- 对历史痕迹类数据没有直接删整行，而是优先将 `knowledge_point_id` 置空，避免丢失教学追溯和学习记录。

## 风险与注意事项

- 当前修复覆盖的是代码中已识别的直接依赖表；若真实运行库存在仓库外的额外约束，仍需继续排查。
- 前端未变更，本轮未重新执行浏览器手动点击验收。

## 验证情况

- 已执行 `mvn -q test`，通过。
- 未执行真实运行库下的手动删除复现。

## 下一位 agent 的接手提示

- 若用户反馈仍报 `500`，优先查看运行库里 `subject_knowledge` 的实际外键/触发器，与 `KnowledgeService.deleteNode` 当前清理范围比对。
- 相关文件优先看：`backend/src/main/java/com/smartedu/service/KnowledgeService.java`、`backend/src/test/java/com/smartedu/service/KnowledgeServiceTest.java`。
