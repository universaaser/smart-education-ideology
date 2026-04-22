# Code Review Hardening Round 2

## 结论

本轮完成了第二阶段代码评审收口，重点清理“默认身份兜底”和“弱类型请求体”两类高风险问题。改动保持最小范围，不改变正常业务链路，但把课程创建、上传任务、学习路径推荐、登录注册、选段解释等入口改成了显式参数约束，降低了串号、误归属和后续维护成本。

## 改动原因

- 对应 `/Draft/毕设.md` 中教师资源上传、课程管理、AI 助教、学生学习路径等闭环能力，需要稳定的身份归属和可维护接口。
- 第一轮评审后仍残留 `userId=1` / `teacherId=1` 这类 demo 兜底，以及 `Map` 请求体带来的弱约束问题，继续保留风险过高。

## 具体改动

- `backend/src/main/java/com/smartedu/controller/AuthController.java`
  - 登录/注册入参改为 DTO：`AuthLoginRequestDto`、`AuthRegisterRequestDto`。
- `backend/src/main/java/com/smartedu/controller/CourseController.java`
  - 创建课程不再默认归属教师 `1`，要求显式 `teacherId`。
- `backend/src/main/java/com/smartedu/controller/PathRecommendController.java`
  - 推荐路径入参改为 `PathRecommendRequestDto`，去掉默认学生 `1`。
- `backend/src/main/java/com/smartedu/controller/UploadController.java`
  - 上传接口不再默认用户 `1`，缺失时明确返回校验错误。
- `backend/src/main/java/com/smartedu/service/ChatService.java`
  - 选段解释服务层移除默认用户回退，并保留服务层保护。
- `services/api.ts`
  - 移除会话创建、会话查询、上传文件的默认 `userId=1`；选段解释仅保留结构化请求。
- `views/ResourceLibrary.tsx`、`views/ResourceUpload.tsx`
  - 调整前端调用，显式透传当前用户/教师 ID。
- `backend/src/test/java/com/smartedu/controller/*.java`、`backend/src/test/java/com/smartedu/service/ChatServiceTest.java`
  - 补充 Auth/Course/Path/Upload 相关回归测试。

## 方案取舍

- 只收紧已确认有真实调用链的入口，不对整仓库所有 `Map<String, Object>` 返回值做大范围 DTO 化。
- 返回结构暂不重构，优先解决身份归属和请求约束这两个更高优先级问题。
- 在关键位置增加少量注释，说明为什么要显式绑定身份，避免后续再把 demo 兜底加回来。

## 风险与注意事项

- 前端构建仍存在单包体积过大警告，尚未做 code splitting。
- `views/ResourceUpload.tsx` 仍然偏大，虽然已收敛重复逻辑，但组件职责仍可继续拆分。
- 仓库内仍存在部分历史中文编码异常，后续处理文案或注释时要先确认编码。

## 验证情况

- 已执行：`mvn -q test`
- 结果：通过
- 已执行：`npm run build`
- 结果：通过，保留既有 chunk size warning
- 未执行：浏览器手动回归

## 下一位 agent 的接手提示

- 若继续深挖评审，优先检查 `views/ResourceUpload.tsx` 组件拆分和前端 chunk 体积优化。
- 其次可继续清点剩余 `Map` 请求体、历史编码异常和异常处理一致性。
