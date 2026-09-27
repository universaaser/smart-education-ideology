# 知识图谱关系列表组件拆分

## 结论

按李洋的任务书，仅将节点详情中的现有关系列表提取为 `KnowledgeRelationList`，未增加功能，知识图谱验收状态保持 `[~]`。

## 改动原因与范围

- 对应 `/Draft/毕设.md` 的知识图谱可视化与管理能力，改善前端可维护性。
- `views/KnowledgeGraph.tsx` 保留关系筛选、API 调用和状态；新增 `components/KnowledgeRelationList.tsx` 负责原列表展示及回调。接口、配置、数据结构均未变，其他模块未修改。
- 选择列表而非整个抽屉，避免移动新增关系表单及画布逻辑。

## 验证与风险

- `node_modules/.bin/tsc.cmd --noEmit`、`npm run build`、`git diff --check` 通过。
- 浏览器模拟图谱回归：教师查看入边/出边、切换关联节点、编辑和删除真实关系，合成关系只读；学生可查看关系且无编辑入口。
- 使用模拟账号和 API；未连接真实后端，仍需真实数据联调及另一名组员 review。浏览器中未启动后端的其他 API 请求报错与本次拆分无关。

## 接手提示

Issue：`https://github.com/universaaser/smart-education-ideology/issues/7`；PR：`https://github.com/universaaser/smart-education-ideology/pull/8`。工作分支为 `codex/liyang-knowledge-graph-relations`，已请求组员 `a3321919` review。合并前仍需对方审查和真实后端联调；不要把模拟回归写成真实服务验收。
