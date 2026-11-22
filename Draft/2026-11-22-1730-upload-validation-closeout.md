# 上传大小边界与文件头校验补做收口

## 结论

王文博的上传校验任务已从仓库根目录的未编译草稿移植到真实后端并纳入 Maven 构建。空文件、超限、扩展名不在白名单、文件头不匹配四类请求均在落盘前拒绝；合法 PDF、DOCX、Markdown 正常落盘并创建解析任务。9 条新增用例全部通过，测试类共 24 条通过。

## 改动原因

- `sprint2-遗留事项.md` §1 定义了验收标准与九条测试矩阵，但此前未找到实现 PR；根目录四份草稿的包名、配置键和端点均与真实后端不一致，`mvn test` 未覆盖。
- 与 `/Draft/毕设.md` 的文档上传入口直接相关：补上外部字节进入系统前的两项非功能缺口。
- 跨平台启动仍按 Issue #3 单独跟踪，本次不碰。

## 具体改动

- 新增 `backend/src/main/java/com/smartedu/util/UploadValidator.java`：空文件/大小上限检查、扩展名精确白名单、PDF/OLE2/ZIP 签名与 Markdown 纯文本兜底。
- `UploadController.uploadFile`：在 `transferTo` 之前串联校验，拒绝码为 `FILE_EMPTY`、`FILE_TOO_LARGE`、`TYPE_NOT_ALLOWED`、`FILE_HEADER_MISMATCH`；扩展名匹配由子串改为完整项。
- `application.yml`：`storage.max-size: 100MB`；multipart 兜底由 50MB/50MB 调整为 100MB/120MB，使接口层先于容器层返回明确业务错误。
- `UploadControllerTest`：新增 9 条用例，覆盖合法三类、空文件、上限 ±1 字节、三类伪装/非法类型；每个拒绝用例断言临时上传目录无新增文件。
- 新增 `weekly-meeting-notes/站会进度补录-2026-10-20.md`、`站会进度补录-2026-11-03.md`：仅归档可核实状态，出席与 Story Points 标为待核实，不臆造点数。
- 同步更新 `Draft/2026-04-16-1337-acceptance-checklist.md` 的上传条目与维护记录。

## 方案取舍

- 只做文件头（magic number）校验，不解析 ZIP 内部部件：任务范围是“现有允许类型的文件头校验”，签名表已覆盖全部允许类型；DOCX/PPTX/XLSX 之间互换扩展名仍可通过，记为未验证风险。
- 复用现有 `Result` 包装与 `standaloneSetup` 测试风格，不引入新依赖、不改接口路径、不新建校验框架。
- 大小上限采用 Spring `DataSize` 绑定，配置键沿用 `storage.*` 命名空间，避免草稿里的 `upload.*` 配置漂移。

## 风险与注意事项

- 未做真实 HTTP 多部分请求与解析服务联调；容器层 100MB 上限的线上行为需在部署环境复核。
- Markdown 无 magic number，纯文本检查可拦住 PNG/PE，但无法区分脚本文本与 Markdown 正文。
- 10/20、11/3 的原始纪要、出席名单与点数记录仍未找到，补录文件不能替代原始证据。

## 验证情况

- `mvn -f backend/pom.xml -Dtest=UploadControllerTest test`（JDK 21 替代本机缺失的 JDK 25，显式覆盖编译目标）：`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- `mvn -f backend/pom.xml test` 全量后端回归：`Tests run: 188, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 9 条新增用例逐条执行目录快照比对：拒绝后临时目录文件数保持 0，合法用例为 1。

## GitHub 交付状态

- 分支 `feature/upload-size-header-validation` 已推送 origin；[Issue #15](https://github.com/universaaser/smart-education-ideology/issues/15) 已建单。原 [PR #16](https://github.com/universaaser/smart-education-ideology/pull/16) 曾合入 main 后按要求撤销；GitHub 不允许重开已合并 PR，故从同一分支新建 [PR #17](https://github.com/universaaser/smart-education-ideology/pull/17) 顶替，当前状态 clean、可干净合并。
- 已请求 `a3321919` 审查；截至 2026-11-22 18:49 尚无 review 提交。
- 审查意见中的 UTF-8 中文误拒、413 响应一致性及次要建议已修复，详见 `Draft/2026-11-22-1849-upload-validation-review-fixes.md`。

## 下一位 agent 的接手提示

- 跟进 PR #17 的审查意见，处理后由作者合并；上线前补真实 HTTP 大文件与解析联调。
- 如需区分 DOCX/PPTX/XLSX 内部部件，再另行扩展校验。
- 10/20、11/3 的点数需轮值人提供原始记录后补入，不能凭难度评级推断。
