# MinerU Parse Mode Label Unify

## 结论

已完成上传页来源文案最小一致性修复：Parse History 列表与结果详情现在统一复用同一套 `parseMode` Tag 映射，页面内 `MINERU / FALLBACK / REGENERATE` 口径一致，空值仍兼容。

## 改动原因

- 该修改属于上传解析链路的可见性收口，对应 `/Draft/毕设.md` 中教师上传文档后查看解析结果的场景。
- 上一轮只补了 Parse History 来源标签，结果详情仍保留旧文案 `Fallback (local extractor)`，同页口径不一致。

## 具体改动

- 修改 `views/ResourceUpload.tsx`
  - 保留已有 `getParseModeTag`
  - 将结果详情卡片标题区的内联分支替换为 `getParseModeTag(correctionDraft?.result.documentStructure?.parseMode)`
  - 不改布局、不改后端、不改其他组件

## 方案取舍

- 直接复用现有 helper，是当前最小改动；避免新增重复映射或额外抽离文件。
- 本次不补前端自动化测试：仓库当前无现成前端测试基建，临时引入会明显扩大范围。

## 风险与注意事项

- 未做浏览器手动点击验收，只完成静态构建校验。
- 若后续还要统一 `MineruStructuredView` 内部来源文案，需要单独检查该组件，但不属于本次范围。

## 验证情况

- 已执行：`npm run build`
- 结果：通过
- 未验证：浏览器内结果详情卡片与 Parse History 的实际视觉一致性

## 下一位 agent 的接手提示

- 若继续收口上传页来源展示，优先检查 `components/MineruStructuredView.tsx` 是否存在另一套 parseMode 文案。
- 相关文件：`views/ResourceUpload.tsx`
