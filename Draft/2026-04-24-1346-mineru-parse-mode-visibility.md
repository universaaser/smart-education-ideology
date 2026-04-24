# MinerU Parse Mode Visibility

## 结论

已完成最小闭环：后端解析历史列表补充 `parseMode`，前端上传页 Parse History 可直接显示 `MINERU / FALLBACK / REGENERATE` 来源标签。旧任务或无 `aiAnalysis` 的任务仍返回空值，不会报错。

## 改动原因

- 解析来源可见性属于上传解析链路的直接完善，对应 `/Draft/毕设.md` 中“利用 MinerU API 对文档进行结构化解析与文本提取”的教师上传场景。
- 现有结果详情已有 `documentStructure.parseMode`，但历史列表缺少来源标识，教师无法快速判断某条任务是否真正走了 MinerU。

## 具体改动

- 修改 `backend/src/main/java/com/smartedu/controller/UploadController.java`
  - `GET /api/upload/tasks` 列表项新增 `parseMode`
  - 从 `task.aiAnalysis` 反序列化 `PipelineResultDto.documentStructure.parseMode`
  - 兼容旧任务、空 `aiAnalysis` 和无 `documentStructure` 的情况，统一返回 `null`
- 修改 `backend/src/test/java/com/smartedu/controller/UploadControllerTest.java`
  - 补充 `listTasks` 回归断言，验证返回 `parseMode=MINERU`
- 修改 `services/api.ts`
  - `ParseTaskListItem` 新增 `parseMode` 字段
- 修改 `views/ResourceUpload.tsx`
  - Parse History 列表新增来源 Tag 映射：`MINERU / FALLBACK / REGENERATE`
  - 无值时不展示，不影响旧任务显示

## 方案取舍

- 采用“列表接口临时解析 `aiAnalysis`”的最小方案，不改数据库结构、不扩散到无关 DTO/Mapper。
- 前端仅在现有 Parse History 标题区增加一个 Tag，不调整布局与交互，避免扩大改动范围。
- 未额外重构已有结果详情来源展示逻辑，本次只收敛用户明确要求的历史列表闭环。

## 风险与注意事项

- 当前 `parseMode` 仍来源于 `aiAnalysis` JSON；若历史脏数据 JSON 非法，则列表会静默返回空值。
- 前端本次未做浏览器手动点击验收，只完成构建校验；真实页面视觉效果仍需运行态确认。
- 本次未新增前端自动化测试，仓库当前也无现成上传页测试基建。

## 验证情况

- 已执行：`mvn -f backend/pom.xml -Dtest=UploadControllerTest test`
- 结果：通过，覆盖 `GET /api/upload/tasks` 返回 `parseMode`
- 已执行：`npm run build`
- 结果：通过
- 未验证：浏览器中 Parse History 来源 Tag 的真实展示与交互

## 下一位 agent 的接手提示

- 若要继续收口“解析来源一致性”，优先检查 `views/ResourceUpload.tsx` 中历史列表与结果详情两处来源展示是否需要统一文案。
- 若后续需要支持更稳定查询，可考虑把 `parseMode` 投影为任务表显式字段，但这不属于本次最小范围。
- 相关文件：`backend/src/main/java/com/smartedu/controller/UploadController.java`、`backend/src/test/java/com/smartedu/controller/UploadControllerTest.java`、`services/api.ts`、`views/ResourceUpload.tsx`
