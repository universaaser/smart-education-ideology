# Course AI Graph Report Closure

## 结论

本次完成课程管理隐藏页、AI 助手历史会话与纯文本标题、知识图谱入库规则、学生 Learning Report 展示口径四项收口，整体达到用户本轮要求的代码层闭环。

## 改动原因

- 对齐 `/Draft/毕设.md` 中教师课程管理、AI 助教、知识图谱更新、学生学习支持闭环。
- 修复原有问题：课程管理缺入口，AI 历史未展示，上传解析污染图谱，学生报告展示页面操作流水。

## 具体改动

- 课程管理：新增隐藏 `COURSE_MANAGEMENT` 视图、Dashboard 编辑入口、课程详情/更新 API、课程学生绑定 API、`CourseManagement` 页面。
- AI 助手：后端回答提示词约束纯文本，首轮消息后生成会话标题；前端展示历史会话、切换、删除、新建。
- 知识图谱：上传解析结果仅保留在 parse task/projection/vector index，不再调用 parse task 入图；资源/新闻入图节点初始位置自动避让；自动匹配思政关系直接 `APPROVED` 以便立即连线。
- 学生报告：最近活动过滤 `page_stay`，增加总学习时间，混入 AI 会话标题并按时间排序。
- 新增/修改接口与类型集中在 `CourseController`、`CourseService`、`services/api.ts`。

## 方案取舍

- 课程管理采用隐藏 enum view，不改 React Router/侧边栏结构，最小化架构扰动。
- 上传解析不删除 `ingestParseTask` 兼容方法，只在主解析流程停止调用，避免影响潜在历史维护入口。
- 新闻连线通过自动批准系统生成匹配实现，保留后续人工审核页面继续修改的能力。

## 风险与注意事项

- AI 纯文本依赖提示词约束，不能绝对保证模型永不输出 Markdown 符号。
- 课程管理允许编辑 `teacherId/status/progress/ideologyScore`，需后续结合权限和业务枚举继续加接口级约束。
- 自动避让只作用于新建资源节点初始坐标，不会强制移动历史重叠节点或人工拖拽后的重叠节点。

## 验证情况

- 已完成代码静态走查和关键 grep：上传解析主流程不再调用 `knowledgeIngestionService.ingestParseTask(task)`；自动匹配设置 `reviewStatus=APPROVED`；前端无本次新增无用 import。
- 待执行：后端 Maven 定向测试/编译、前端 `npm run build`、浏览器手动验收。

## 下一位 agent 的接手提示

- 优先运行 `mvn -f backend/pom.xml test` 或定向测试与 `npm run build`。
- 手动验收路径：Dashboard 课程编辑进入隐藏页；AI 历史会话切换/删除/首轮标题；上传文档后图谱不新增文档节点；新闻资源入图初始不重叠并立即连思政节点；学生首页不出现页面访问流水。