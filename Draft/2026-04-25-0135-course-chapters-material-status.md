# Course Chapters, Material Binding, and Status Summary

## 结论

完成 PRD 第 3 包必做范围：课程章节、材料版本章节归属、资源库章节分组、上传/编辑页章节选择和课程状态汇总已经形成 demo 闭环。3.7 章节知识点直接绑定仍保持可选未做，不影响本包完成边界。

## 改动原因

- 对应 `/Draft/毕设.md` 中教师侧课程资源组织、教学内容生成与知识图谱关联展示能力。
- 对应 `Draft/PRD.md` 第 3 包，让“课程 -> 章节 -> 材料版本”的组织关系在教师端可见。

## 具体改动

- 后端新增 `CourseChapter`、`CourseChapterMapper`、章节 DTO 和 `course_chapters` 迁移脚本。
- `CourseController`/`CourseService` 新增章节列表、新增、编辑、材料绑定章节和课程状态汇总接口。
- `TeachingMaterial`、保存请求、草稿/视图 DTO、课程材料分组 DTO 增加 `chapterId`，保存/发布/回滚时保留章节归属。
- `ResourceLibrary` 按课程章节分组展示材料版本，未绑定材料进入 `Unassigned Materials`。
- `ResourceUpload` 按课程加载章节，保存草稿或发布版本时提交章节归属。
- 已同步 `Draft/PRD.md`、验收清单和 `Draft/CHANGE-INDEX.md`。

## 方案取舍

- 采用 `teaching_materials.chapter_id` 作为最小持久化关系，避免新增绑定表和历史版本冲突治理。
- 章节第一版只做列表、新增、编辑和简单排序，不做拖拽、删除/禁用或章节知识点绑定。
- 状态汇总优先返回可由现有表稳定聚合的数据：章节、解析任务、材料版本、草稿、发布和课程知识点数量。

## 风险与注意事项

- 目标数据库仍需执行 `migration_course_chapters.sql`；历史材料 `chapter_id` 默认为空，会展示在未分配分组。
- 未做真实图谱同步状态，只展示知识点数量等聚合指标。
- 未做浏览器手动验收，运行态交互仍需人工检查。

## 验证情况

- 已通过后端定向测试：`mvn -f backend/pom.xml -Dtest=CourseControllerTest,TeachingMaterialControllerTest,TeachingMaterialServiceTest test`。
- 已通过前端构建：`npm run build`。
- 自审中修正了状态汇总缺少解析任务数量的问题，并将迁移脚本中的索引新增语法收敛为普通 `ADD INDEX` 以降低 MySQL 方言风险。

## 下一位 agent 的接手提示

- 不要继续扩大第 3 包；若继续按 PRD，应进入第 4 包“知识源配置、抓取日志与人工审核”。
- 若先补第 3 包质量，可优先做浏览器手动验收、目标库迁移验证、章节知识点绑定或真实图谱同步状态展示。