# Upload Editor Save and UI Fix

## 结论

已修复资源上传完成页的教学材料保存与编辑体验问题：去掉面向用户无价值的 `AI Analysis` 展示，拉宽 `Chapter Outline`、`Teaching Focus` 和 `Cases` 输入行，并让 `Save Draft` / `Save Version` 对空请求、业务校验失败和无效 trace 数据不再直接表现为 500。

## 改动原因

- 用户反馈 Markdown 上传修复后，资源上传完成页仍存在展示冗余和输入框过窄问题。
- `Save Draft` 与 `Save Version` 在 `/api/upload/tasks/{taskId}/editor-draft`、`/api/upload/tasks/{taskId}/materials` 返回 500，影响教师编辑保存闭环。

## 具体改动

- `UploadController`：保存草稿和发布版本接口补异常收口，业务校验错误返回 `badRequest`，未预期错误记录日志并返回统一失败信息。
- `TeachingMaterialService`：保存前归一化空请求；保存材料前校验任务必须有 `userId`；同步 trace 行时跳过缺少必填知识点名或思政元素的无效记录，避免数据库约束异常。
- `TeachingMaterialServiceTest`、`UploadControllerTest`：补空请求保存、缺失 userId、无效 trace 行、发布校验失败响应等回归测试。
- `ResourceUpload`：移除上传完成页的 `AI Analysis` 卡片；移除 Teaching Editor 中重复的 Trace Summary 展示入口。
- `ParseResultCorrectionCard`：`Chapter Outline`、`Teaching Focus` 每行改为 flex 布局，输入框占满 Remove 按钮外的剩余宽度。
- `TeachingMaterialEditorCard`：`Cases` 文本框改为 flex 布局，占满整行剩余宽度。

## 方案取舍

采用最小 UI 和保存链路修复：不改接口字段名，不调整教学材料数据模型，不删除后台 `aiAnalysis` 存储，只停止在用户页面展示；Teaching Editor 保留最终材料编辑与保存，移除重复只读 trace 展示以降低页面重复感。

## 风险与注意事项

- Trace Summary 展示入口从 Teaching Editor 移除后，trace 数据仍由后端保存和导出链路维护，但上传编辑页不再提供该区域筛选浏览。
- 发布版本仍会对选择题选项、答案匹配和课程规则做严格校验；前端收到的是业务错误而不是 500。
- 尚需运行定向测试和前端构建，并在真实浏览器中手动验证上传完成页保存体验。

## 验证情况

- `mvn -f backend/pom.xml test -Dtest=UploadControllerTest,TeachingMaterialServiceTest,AiIntelligenceServiceTest` 通过，46 tests / 0 failures / 0 errors。
- `npm run build` 通过。

## 下一位 agent 的接手提示

- 后端定向测试和前端构建已通过。
- 若继续验收，请启动前后端并在浏览器上传 `.md` 文件，确认完成页无 `AI Analysis`，输入行变宽，`Save Draft` 成功，`Save Version` 对合法内容成功、对非法题目返回业务错误。
