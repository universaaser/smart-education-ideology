# Knowledge Graph Layout Display Optimization

## 结论

本轮完成了知识图谱的显示优化和新增节点基础闭环。图谱连线不再显示关系文字，节点改为显示完整多行标题；教师/管理员可直接在图谱页新增节点并选择大小，新节点默认会避开已有节点位置。

## 改动原因

- 本次任务直接对应 `/Draft/毕设.md` 中知识图谱的可视化展示与管理能力。
- 现状存在四个明显问题：连线文字干扰阅读、节点标题被截断、新增节点缺少前端入口、未传坐标时新节点容易与原有节点重叠。

## 具体改动

- 修改 `views/KnowledgeGraph.tsx`：
  去掉连线文字；节点标题改为完整多行展示；新增 `Add Node` 弹窗表单；新增节点后自动刷新并聚焦到新节点。
- 修改 `services/api.ts`：
  新增 `knowledgeApi.createNode` 和 `KnowledgeNodeCreateRequest`。
- 修改 `backend/src/main/java/com/smartedu/service/KnowledgeService.java`：
  新增节点时若未指定坐标，则按网格候选位自动寻找非重叠位置；同时统一规范节点大小值。
- 修改 `backend/src/test/java/com/smartedu/service/KnowledgeServiceTest.java`：
  新增“自动避让 + 尺寸保留”回归测试。

## 方案取舍

- 采用最小闭环方案，在现有图谱页直接补一个轻量新增节点表单，而不是扩展成完整图谱编辑器。
- 完整标题使用多行 SVG 文本展示，没有引入额外布局库或复杂碰撞引擎。
- 自动避让采用后端默认布局，这样手工新增和 Excel 导入都能复用同一规则。

## 风险与注意事项

- 当前完整标题展示以可读性优先，极端长标题在高密度区域仍可能显得拥挤。
- 自动避让是规则化布局，不是物理引擎，后续若节点数量继续增多，可能还需要更强的布局策略。
- 本轮没有顺手扩展关系编辑、节点编辑或批量布局能力。

## 验证情况

- 已执行 `npm run build`，通过。
- 已执行 `mvn -q test`，通过。
- 未执行浏览器手动点击验证。

## 下一位 agent 的接手提示

- 如果继续做图谱编辑能力，优先查看 `views/KnowledgeGraph.tsx`、`services/api.ts`、`backend/src/main/java/com/smartedu/service/KnowledgeService.java`。
- 若要进一步优化视觉密度，重点考虑长标题的布局策略和节点区域碰撞算法。
