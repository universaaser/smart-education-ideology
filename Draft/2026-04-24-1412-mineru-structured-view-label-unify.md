# MineruStructuredView Label Unify

## 结论

已完成 `MineruStructuredView` 来源标签的一致性收口：组件头部 `parseMode` 现在统一显示 `MINERU / FALLBACK / REGENERATE`，与 `ResourceUpload` 页面其他区域保持一致，空值仍兼容。

## 改动原因

- 该修改属于上传解析链路来源展示一致性的继续收口，对应 `/Draft/毕设.md` 中教师上传文档后查看结构化解析结果的场景。
- 之前 `ResourceUpload` 已统一列表和结果详情文案，但 `MineruStructuredView` 仍保留旧文案 `MinerU / Fallback (local extractor)`，导致页面内仍有第二套口径。

## 具体改动

- 修改 `components/MineruStructuredView.tsx`
  - 在组件内新增最小 `parseModeTag` 映射
  - `MINERU` → `geekblue / MINERU`
  - `FALLBACK_LLM` → `orange / FALLBACK`
  - `REGENERATE` → `purple / REGENERATE`
  - 其他值或空值保持兼容，返回 `null`
  - 头部 Tag 改为直接渲染 `parseModeTag`

## 方案取舍

- 采用组件内最小 helper 方案，不做跨文件抽取，避免扩大到公共工具或改动 `ResourceUpload`。
- 不改后端、不改接口、不做页面结构调整，只收口同一页面的来源标签文案。

## 风险与注意事项

- 当前 `MineruStructuredView` 与 `ResourceUpload` 仍分别维护一份相同映射；若后续还有其他页面要展示 `parseMode`，可再考虑抽成共享 helper，但不属于本次最小范围。
- 未做浏览器手动点击验收，只完成前端构建验证。

## 验证情况

- 已执行：`npm run build`
- 结果：通过
- 未验证：浏览器中 `MineruStructuredView` 头部 Tag 的真实显示效果

## 下一位 agent 的接手提示

- 若后续继续收口来源展示，优先检查是否还有其他组件直接写死 `parseMode` 文案。
- 相关文件：`components/MineruStructuredView.tsx`
