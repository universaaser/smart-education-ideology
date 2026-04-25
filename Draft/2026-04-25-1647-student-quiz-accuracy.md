# Student Quiz Accuracy

## 结论

已按 `Draft/2026-04-25-1633-prd-demo-richness-plan.md` 的 P0-1 完成学生测验与答题正确率最小闭环：学生首页可读取课程材料单选题、提交答案、获得正确/错误反馈，学习报告聚合答题次数、正确率和薄弱知识点，预警服务可基于低正确率生成建议。

## 改动原因

- 补齐 PRD 中答题正确率与验收清单里“真实答题入口缺失”的短板。
- 与 `/Draft/毕设.md` 的关系：属于学生学习监测、学习画像和预警反馈链路的功能完善。

## 具体改动

- 新增 `StudentQuizController`、`StudentQuizService` 和 3 个测验 DTO。
- 扩展 `StudentActivityEventService` 与 `StudentLearningReportDto`，从 `answer_submit` 事件聚合测验统计。
- 扩展 `AlertService`，近 7 日答题数不少于 3 且正确率低于 50% 时生成 `LOW_QUIZ_ACCURACY`。
- 扩展 `services/api.ts` 与 `views/StudentHome.tsx`，在学生首页内嵌 `Practice / Quiz` 卡片。
- 新增 `StudentQuizControllerTest`，并更新受 DTO/构造函数影响的既有测试桩。
- 未新增数据库表，复用 `student_activity_events.payload_json` 保存题目快照、答案、正确性和知识点。

## 方案取舍

- 采用复用事件流而非新增 `student_quiz_attempts`/`student_quiz_answers`，因为 P0-1 只要求最小闭环，现有事件表已能追溯 `studentId`、`courseId`、题目快照、`knowledgePointId` 和 `isCorrect`。
- 只支持 `SINGLE_CHOICE`，不实现简答题自动评分、限时考试、排行榜和教师发布入口，避免超出计划文档边界。
- 学生入口直接放在 `StudentHome`，避免新增路由、权限和独立页面。

## 风险与注意事项

- 答案匹配按首字母/标准答案的大写字符串比较，要求教学材料选择题参考答案与选项字母保持一致。
- 未执行真实浏览器端到端验收，仍需准备含单选题的课程材料后手动提交至少 3 题。
- 未新增数据库结构，因此未触发数据库迁移。

## 验证情况

- 已新增控制器测试覆盖题目读取、提交答案、空题目材料参数和空答案。
- 待执行完整后端测试、前端构建和浏览器手动验收。

## 下一位 agent 的接手提示

- 优先运行 `mvn -f backend/pom.xml test -Dtest=StudentQuizControllerTest,StudentActivityEventControllerTest,AlertControllerTest` 与 `npm run build`。
- 手动验收路径：学生登录后进入 `StudentHome`，在 `Practice / Quiz` 选择含单选题的课程材料，提交至少 3 题，刷新后检查正确率、最近活动和反馈建议。
