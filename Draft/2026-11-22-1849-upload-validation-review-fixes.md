# 上传校验审查意见修复收口

## 结论

针对 PR #16 / #17 的审查意见逐条处理完成：阻塞项中的合并冲突经核查已不存在（活跃 PR #17 为 clean），UTF-8 中文 Markdown 误拒 Bug 已修复并补测；容器层与业务层 413 响应不一致的隐患已通过「容器上限高于业务上限 + 全局异常处理」双层方案解决。全量后端回归 **191 条通过，BUILD SUCCESS**。

## 审查意见处理明细

### 1. 合并冲突（CONFLICTING / DIRTY）— 已核查，无需再处理

- PR #16 曾因主干合入 PR #14（`049a99a`，教学材料草稿重构）在 `Draft/2026-04-16-1337-acceptance-checklist.md` 与 `Draft/CHANGE-INDEX.md` 出现冲突；此前已把该主干合并进功能分支并解决 `CHANGE-INDEX.md` 的唯一冲突。
- 当前活跃 PR 为 [#17](https://github.com/universaaser/smart-education-ideology/pull/17)（原 #16 曾合并后被要求撤销，GitHub 不允许重开已合并 PR，故从同一分支新建顶替 PR）。实时 API 状态：`mergeable: true`、`mergeable_state: clean`、head `46a8acd3`、base `main`、4 commits / 10 files。
- 两个争议文件均无冲突标记，且同时包含 PR #14 的索引/验收记录与本次上传校验记录。已确认基于最新 main（`049a99a`），未再执行重复 merge/rebase。

### 2. 严重 Bug：中文 Markdown 被误判为控制字符 — 已修复

- `UploadValidator.isReadableText` 原实现用有符号 `byte value = sample[i]` 与 `0x20` 比较；UTF-8 中文等非 ASCII 字节（0x80~0xFF）为负数，全部落入控制字符分支。
- 改为 `int unsigned = sample[i] & 0xFF` 无符号判断；按建议遇到 `0x00` NUL 直接拒绝，其余 `< 0x20` 且非 `\t`、`\n`、`\r` 的字节才计入控制字节，阈值仍为 10%。
- 新增 `shouldAcceptChineseMarkdown`：`# 思政课程笔记` 等中文 UTF-8 内容以 `.markdown` 扩展名上传，断言 200 且落盘 1 个文件。原有 `shouldRejectPngRenamedAsMarkdownWithoutWriting` 保留并通过：PNG 头含 NUL 字节，仍返回 `FILE_HEADER_MISMATCH` 且目录 0 文件。

### 3. 重要隐患：容器层 413 与业务层不一致 — 已修复

- `application.yml`：`spring.servlet.multipart.max-file-size` 由 100MB 调为 **105MB**，`max-request-size` 由 120MB 调为 **125MB**，均高于 `storage.max-size: 100MB`。这样 100MB+1byte 的真实 HTTP 请求会先进入控制器，由 `checkSize` 返回统一 `Result.error(413, "FILE_TOO_LARGE: ...")`。
- 新增 `backend/src/main/java/com/smartedu/common/UploadExceptionHandler.java`：`@RestControllerAdvice` + `@ExceptionHandler(MaxUploadSizeExceededException.class)`，对超过容器上限的请求同样返回 `code=413`、消息含 `FILE_TOO_LARGE` 的统一信封，避免 Spring 默认错误页。
- 新增 `UploadExceptionHandlerTest.shouldReturnUnified413EnvelopeForContainerSizeExceeded` 断言 413 与错误码文案。

### 4. 次要建议 — 已处理 3 项，保留 1 项

- 大小提示格式化：由直接拼接 `DataSize`（`104857600B`）改为 `String.format(Locale.ROOT, "%dMB", maxFileSize.toMegabytes())`，输出 `100MB`。当前 Spring 版本的 `toMegabytes()` 返回 `long`，故用 `%d`。
- 新增错误消息英文化：按 `AGENTS.md` §3.1，全部新增拒绝消息改为英文（`FILE_EMPTY: No file was provided`、`FILE_TOO_LARGE: File size exceeds the limit (max 100MB)`、`TYPE_NOT_ALLOWED: Unsupported file type; allowed: ...`、`FILE_HEADER_MISMATCH: File content does not match its type`、`FILE_HEADER_READ_ERROR: Failed to read file`）。原有预存消息未改动。
- `UploadValidator.isAllowedType`：增加 `extension.isBlank()` 防御，并过滤白名单拆分出的空项，避免配置尾随逗号放行无扩展名文件；新增 `UploadValidatorTest` 覆盖。
- 根目录 4 份旧草稿（`9 test`、`Controller`、`Magic Number`、`Size Boundary Interceptor`）暂不删除：它们是本次补做任务的原始依据，审查意见标注为非阻塞且条件性（"如不再需要"）。如需清理请明确确认，再由维护者决定。

## 验证情况

- 环境：本机无 JDK 25 / Maven，使用 JDK 21 + Maven 3.9.9，显式覆盖 `java.version` / `maven.compiler.*` 为 21。
- `mvn -f backend/pom.xml -Djava.version=21 -Dmaven.compiler.source=21 -Dmaven.compiler.target=21 -Dmaven.compiler.release=21 test`：**Tests run: 191, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS。
- 用例数由 188 增至 191：新增中文 Markdown、全局 413 信封、空白扩展名防御三条。
- 所有拒绝类用例仍断言临时上传目录无新增文件；PNG 伪装 `.md` 仍被拦截。

## 未验证 / 后续

- 仍未做真实 HTTP 多部分请求与解析服务联调；105MB 容器上限上线前需在部署环境复核（容器/网关层可能另有独立限制）。
- Markdown 纯文本检查现在允许任意非 NUL 的高低字节，不校验 UTF-8 序列合法性；如需严格文本校验可另行扩展。
- DOCX/PPTX/XLSX 仍只校验 ZIP 容器头，不交叉校验内部部件。
- 审查中的 review 留痕：此前请求的 reviewer `a3321919` 尚无 review 提交，如有需要可在 PR #17 补 review。
