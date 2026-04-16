# Material Version Export Markdown

## 结论
本次完成“导出 + 版本闭环”剩余能力：教师可在上传完成页查看版本历史、回读指定版本继续编辑，并可对已保存版本导出 Markdown。目标范围已达成，DOCX 导出按计划延期。

## 改动原因
- 上一阶段已完成编辑与保存，但缺少版本回看和可交付导出，教师闭环仍不完整。
- 与 `/Draft/毕设.md` 对应：补齐智能教学内容生成在教师侧“可留档、可交付”的关键一环。

## 具体改动
- 后端：
  - 新增 `GET /api/upload/tasks/{taskId}/materials` 返回版本列表。
  - 新增 `GET /api/materials/{materialId}/export/markdown` 下载 Markdown。
  - `TeachingMaterialService` 增加版本列表查询、Markdown 内容生成、导出文件名生成。
- 前端：
  - `ResourceUpload` 新增版本历史区、版本回看动作、Markdown 导出按钮。
  - `services/api.ts` 增加版本列表查询与 markdown blob 下载方法。
- 测试：
  - 新增/扩展 controller 与 service 测试覆盖版本列表与导出响应。

## 改动原因说明
- 保持最小改动：复用既有 `TeachingMaterialService` 与上传页，不新增依赖、不更换框架。
- 导出先做 Markdown，是为了优先形成稳定接口与最小可交付能力。

## 风险与注意事项
- 未完成项：DOCX 导出、追溯检索拆表仍待下一轮。
- 风险：并发编辑同一任务仍无乐观锁；导出文件名使用安全化英文 slug，不保留中文原名。
- 兼容性：旧上传、轮询、`result-detail`、`regenerate` 接口语义保持不变。

## 验证情况
- 已执行：
  - `backend`: `mvn -q test` 通过
  - `backend`: `mvn -q -DskipTests package` 通过
  - `frontend`: `npm run build` 通过
- 未验证：
  - 真实部署环境下浏览器下载文件名在不同系统区域设置中的一致性。

## 下一位 agent 的接手提示
- 下一步建议优先做 DOCX 导出，输入可直接复用 `TeachingMaterialViewDto`。
- 关键文件：
  - `backend/src/main/java/com/smartedu/service/TeachingMaterialService.java`
  - `backend/src/main/java/com/smartedu/controller/TeachingMaterialController.java`
  - `views/ResourceUpload.tsx`
  - `services/api.ts`
- 若需支持追溯筛选检索，建议新增独立关系表而非继续扩展 `trace_json`。
