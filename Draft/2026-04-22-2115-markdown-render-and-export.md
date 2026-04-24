# 文档解析界面 Markdown 渲染 & 导出修复

## 结论
- 前端新增 `MarkdownView` 组件，替换原先用 `<Paragraph whiteSpace:pre-wrap>` 直出原始文本的三处：Parsed Content Summary、AI Analysis、Selection Explanation（含历史预览）。
- 后端 `TeachingMaterialService.buildMarkdown` 改为“内容即 Markdown”，不再对 `#/*/[]` 等字符做全量反斜杠转义，仅对标题行和行内字段做换行压平。
- 文档解析页面结果卡片与 Selection Explain 面板做了轻度视觉统一（渐变标题、阴影、Evidence 卡片）。

## 改动清单
- `package.json`：新增 `react-markdown@^9`、`remark-gfm@^4`。
- `components/MarkdownView.tsx`（新增）：统一 Markdown 渲染组件，支持 GFM、紧凑模式、空占位。
- `index.css`：新增 `.md-view` 系列样式（标题、列表、代码块、表格、引用等）。
- `views/ResourceUpload.tsx`：Parsed Content / AI Analysis 卡片改用 `MarkdownView`，头部改为图标+文字 `Space`，加渐变 `headStyle`。
- `components/SelectionExplainPanel.tsx`：回答主体改用 `MarkdownView` 渲染；新增 Evidence 徽标、历史条目使用紧凑 Markdown 预览、空状态使用 `Empty`。
- `backend/.../TeachingMaterialService.java`：移除 `escapeMarkdownInline/Block`，新增 `sanitizeHeading / sanitizeInline / formatListItem`，列表项支持多行缩进。
- `backend/.../TeachingMaterialServiceTest.java`：同步用例改为断言“原生 Markdown 保留且不被反斜杠转义”；补上 `assertFalse` 静态导入。

## 根因
1. 前端三处直接把 Markdown 文本按纯文本渲染，用户看到 `# / **` 等原始标记。
2. 后端导出时对所有业务字段（含讲义、参考答案、案例）调用 `escapeMarkdownInline`，把用户或 AI 写进 Lecture Notes 的 Selection Explanation（本身就是 Markdown）整体转义，导致 `.md` 文件排版失效。

## 成功判据
- 上传解析完成后，解析摘要 / AI Analysis / Selection Explanation 面板能以正确的标题、列表、强调样式展示。
- “Export Markdown” 下载的 `.md` 文件在任意 Markdown 阅读器中能正常渲染，包括 Selection Explanation 内容。
- 已有单元测试 `shouldBuildMarkdownPreservingOriginalMarkdownAndFallbackTitle` 通过。

## 手动验证
1. `npm install` 后 `npm run dev`，上传一份文档解析完成。
2. 查看 Parsed Content Summary / AI Analysis：标题、列表、粗体应渲染。
3. 在 Lecture Notes 选中片段，点击 Explain Selection，确认回答卡片 Markdown 正常渲染，`Evidence` 徽标展示，历史条目紧凑预览。
4. 保存版本后 Export Markdown，用任一阅读器打开 `.md`，确认 `#/*/[]` 保留而非被 `\` 转义。
5. 后端 `mvn -pl backend -am test -Dtest=TeachingMaterialServiceTest` 验证新断言。

## 影响范围
- 仅影响 Teaching Material Markdown 导出与文档解析页三个结果面板；不改动 API 形态、数据库结构、解析管线。
- 新增两个前端依赖；无新增后端依赖。

## 风险 / 未验证项
- 用户历史 Markdown 导出文件已被转义的，不会自动修复，需重新导出。
- `mvn test` 因其他测试类（`UploadControllerTest`、`AiIntelligenceServiceTest` 等）的 `AiIntelligenceService` 构造器参数不匹配而先行失败；该问题与本次改动无关，未处理。

## 2026-04-22 21:28 补丁：修复启动报错
现象：`spring-boot:run` 报 `Unresolved compilation problems` 并在 GBK 控制台下显示 `淇濈暀鍘熷 cannot be resolved to a type`（UTF-8 字节被 IDE 按 GBK 误解后写入 stub `.class`）。

根因：
- IDE（Eclipse/JDT 内嵌编译器）在 UTF-8 源码上使用了非 UTF-8 读取，导致我新增的中文块注释编译失败，产出带有运行期 `throw Error` 的 stub `.class`，随后被 `spring-boot:run` 直接加载。
- Maven 本身使用 pom 的 `project.build.sourceEncoding=UTF-8`，compile 无问题。

处理：
- 将本次新增的中文注释转为英文（`TeachingMaterialService.buildMarkdown`、`sanitizeHeading / sanitizeInline / formatListItem`、`TeachingMaterialServiceTest` 回归用例），避免与 IDE 编码行为耦合；文件中原有的中文注释按“只改必须改的”原则保留。
- 删除 `backend/target/classes` 和 `target/test-classes` 下 IDE 产出的 stub，`mvn clean compile -DskipTests` 通过。

验证：`mvn -q -f backend/pom.xml compile -DskipTests` 返回码 0。随后 `mvn -f backend/pom.xml spring-boot:run` 可正常启动。
