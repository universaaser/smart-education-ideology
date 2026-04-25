# Knowledge Relation Governance

## 结论

完成 PRD 第 9 包“知识图谱关系编辑、重复提示与撤销”的 demo 闭环：教师可在图谱节点详情查看入边/出边、编辑真实关系类型、删除关系，并撤销最近一次关系编辑或删除。

## 改动原因

- 对应 `/Draft/毕设.md` 的知识图谱展示与维护能力。
- 对应 `Draft/PRD.md` 第 9 包 9.1-9.7。

## 具体改动

- 新增 `knowledge_change_logs` 表、实体、Mapper、迁移脚本，并同步主 `schema.sql`。
- `KnowledgeController` 新增关系编辑与最近撤销接口，创建/编辑/删除关系均返回明确业务错误。
- `KnowledgeService` 增加关系类型校验、旧中文类型归一、重复关系校验、关系编辑/删除日志和最近一次撤销。
- `KnowledgeGraph` 节点详情改为关系治理列表，真实关系可编辑/删除，负 id 合成关系只读；顶部新增 `Undo Relation Change`。
- `services/api.ts` 补充关系编辑、删除、撤销接口和关系描述/权重字段。

## 方案取舍

- 只记录关系更新/删除的 before/after JSON，不做复杂审计、多人冲突或任意历史回放。
- 合成的 `subject_ideology_matches` 关系仍以负 id 只读展示，不纳入编辑/删除/撤销。
- 撤销删除通过恢复逻辑删除行完成，避免自增主键重复插入。

## 风险与注意事项

- 目标数据库仍需执行 `migration_knowledge_change_logs.sql`。
- 未做浏览器端真实登录手动验收。
- 节点字段编辑增强、导入历史、多人冲突处理仍是后续项。

## 验证情况

- `mvn -f backend/pom.xml -Dtest=KnowledgeControllerTest,KnowledgeServiceTest,PathRecommendControllerTest test`：11 tests，0 failures，BUILD SUCCESS。
- `npm run build`：通过，生成 `KnowledgeGraph` 懒加载 bundle。
- 代码审查修复：关系类型校验保留旧中文类型归一，避免破坏 Excel 导入兼容；撤销删除改为恢复逻辑删除行，避免主键冲突。

## 下一位 agent 的接手提示

第 9 包已按 PRD demo 边界完成。下一步建议继续第 10 包“README、演示脚本与测试用例表”，相关路径包括 `README.md`、`Draft/PRD.md`、`Draft/2026-04-16-1337-acceptance-checklist.md`，并覆盖教师、学生、知识库更新和管理员四条演示主线。
