# Course Material Rules

## 结论

本轮完成 P0 教学内容质量的课程级规则最小闭环：有课程规则时，发布正式教学材料会校验讲义长度、必需章节和案例思政标签；无规则时保持原保存行为。

## 改动原因

- 对应 `/Draft/毕设.md` 中“智能教学内容生成与课程设计辅助系统”的教学讲义、案例和思政融入质量要求。
- 题目完整性与选择题答案保护已完成，本轮补其上游课程级质量约束。

## 具体改动

- 新增 `CourseMaterialRule`、`CourseMaterialRuleMapper` 与 `migration_course_material_rules.sql`。
- `TeachingMaterialService.savePublishedVersion` 发布前读取 `course_material_rules.rule_json`，支持 `minLectureCharacters`、`requiredSections`、`requireIdeologyTagInCases`。
- 补充 `TeachingMaterialServiceTest` 规则失败/通过回归，并适配相关控制器测试桩。
- 同步更新验收清单维护记录。

## 方案取舍

- 采用“有规则才校验”的最小方案，避免现在就新增配置页面或规则引擎。
- 规则以 JSON 保存，便于后续配置入口扩展；服务层只支持当前验收需要的三项固定规则。
- 审查后复用已加载 `ParseTask`，去掉 mapper null 兜底，并为课程最新规则查询补 `(course_id, updated_at, id)` 索引。

## 风险与注意事项

- 目标数据库需执行 `migration_course_material_rules.sql`。
- 当前未实现规则管理接口/页面，也未把规则注入 AI 生成提示或做自动重生成。
- 案例思政标签当前识别 `[Ideology: ...]` 或 `Ideology tag:`，后续如前端配置化应统一标签规范。

## 验证情况

- 已执行：`mvn -f backend/pom.xml -Dtest=TeachingMaterialServiceTest,UploadControllerTest,CourseControllerTest,TeachingMaterialControllerTest test`。
- 结果：31 个测试通过。
- 未验证：浏览器端发布失败提示与真实数据库迁移执行。

## 下一位 agent 的接手提示

- 下一步可做 P0 剩余：规则配置入口、AI 生成 prompt 注入课程规则、不满足规则时一次重生成或明确返回教师可读原因。
