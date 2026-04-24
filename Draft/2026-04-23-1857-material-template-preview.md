# Material Template Preview

## 结论

本次计划补齐教学材料模板化排版：Markdown 导出和页面预览统一为正式材料结构，讲义、案例、题目和思政融入区块具备稳定层级。目标是在不新增模型能力、不改数据库和接口的前提下，提高交付观感。

## 改动原因

- 用户要求“讲义/案例/题目生成后看起来像正式材料”。
- 与 `/Draft/毕设.md` 中“智能教学内容生成与课程设计辅助系统”直接对应。

## 具体改动

- `TeachingMaterialService` 的 Markdown 导出固定为 `Lecture Notes`、`Teaching Cases`、`Assessment Questions`、`Ideology Integration`。
- 新增 `components/TeachingMaterialPreview.tsx`，上传编辑页和课程资源库预览复用同一组件。
- `index.css` 新增局部预览样式，不改全局主题。
- `TeachingMaterialServiceTest` 补导出版式回归断言。

## 方案取舍

- 未新增模板配置、数据库字段或导出格式，避免扩大实现面。
- 复用现有 `MarkdownView`，保留 AI/教师已写入的 Markdown 语义。

## 风险与注意事项

- 第七项仍为 `[~]`，因为课程级质量规则、人工审核和完整题型约束仍未完成。
- 仍需浏览器手动检查真实长内容下的视觉表现。

## 验证情况

- 已执行 `mvn -q test`，通过。
- 已执行 `npm run build`，通过。
- 未执行真实浏览器手动点击验收。

## 下一位 agent 的接手提示

- 优先看 `components/TeachingMaterialPreview.tsx`、`TeachingMaterialService.buildMarkdown` 和验收清单第七项。
- 后续若继续提升正式材料能力，应优先补题型约束、课程级质量规则和人工审核。
