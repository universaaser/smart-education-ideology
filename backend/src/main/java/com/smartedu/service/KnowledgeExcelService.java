package com.smartedu.service;

import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.entity.KnowledgeRelation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱 Excel 导入导出服务
 *
 * <p>
 * 支持从 Excel 模板批量导入学科知识节点和节点关系，
 * 同时提供 Excel 模板生成功能，帮助用户规范填写。
 *
 * @author SmartEducation Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeExcelService {

    private final KnowledgeService knowledgeService;

    /**
     * Excel 模板列定义
     *
     * <p>
     * 节点表（Sheet: 知识节点）：
     * 名称 | 学科 | 分类 | 知识简介 | 思政关联 | 节点类型(TECH/IDEO) | 图标 | 副标题 | 节点大小(LG/MD/SM)
     *
     * 关系表（Sheet: 知识关系）：
     * 起点节点名称 | 终点节点名称 | 关系类型(TECH_BASE/VALUE_SHOW/THEORY_SUPPORT/PRACTICE_APPLY)
     */
    private static final String[] NODE_HEADERS = {
            "名称*", "学科*", "分类", "知识简介", "思政关联", "节点类型(TECH/IDEO)", "图标", "副标题", "节点大小(LG/MD/SM)"
    };

    private static final String[] RELATION_HEADERS = {
            "起点节点名称*", "终点节点名称*", "关系类型(TECH_BASE/VALUE_SHOW/THEORY_SUPPORT/PRACTICE_APPLY)*"
    };

    // 模板示例数据（帮助用户理解填写规则）
    private static final Object[][] NODE_EXAMPLES = {
            { "物联网传感器", "物联网技术", "感知层", "传感器是物联网系统采集物理世界数据的核心器件，包含温度、湿度、压力等类型。", "体现工匠精神与科技报国使命，是国产核心器件突破的典型案例。", "TECH", "sensors", "物联网感知", "MD" },
            { "科技报国", "思政教育", "家国情怀", "鼓励学生将专业技能与国家战略发展相结合，勇担技术兴国的时代责任。", "", "IDEO", "flag", "思政元素", "MD" },
    };

    private static final Object[][] RELATION_EXAMPLES = {
            { "物联网传感器", "科技报国", "VALUE_SHOW" },
    };

    /**
     * 生成 Excel 模板并返回字节数组
     *
     * @return 模板文件的字节数组
     */
    public byte[] generateTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 创建通用样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle exampleStyle = createExampleStyle(workbook);
            CellStyle noteStyle = createNoteStyle(workbook);

            // Sheet1: 知识节点
            Sheet nodeSheet = workbook.createSheet("知识节点");
            createSheet(nodeSheet, NODE_HEADERS, NODE_EXAMPLES, headerStyle, exampleStyle, noteStyle, workbook);

            // Sheet2: 知识关系
            Sheet relationSheet = workbook.createSheet("知识关系");
            createSheet(relationSheet, RELATION_HEADERS, RELATION_EXAMPLES, headerStyle, exampleStyle, noteStyle, workbook);

            // 说明 Sheet
            Sheet guideSheet = workbook.createSheet("填写说明");
            createGuideSheet(guideSheet, workbook);

            workbook.write(out);
            log.info("Excel 模板生成成功");
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Excel 模板生成失败", e);
            throw new RuntimeException("模板生成失败: " + e.getMessage());
        }
    }

    /**
     * 解析上传的 Excel 文件，批量导入知识图谱节点和关系
     *
     * @param inputStream Excel 文件流
     * @return 导入结果统计
     */
    public ImportResult importFromExcel(InputStream inputStream) {
        ImportResult result = new ImportResult();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            // 解析节点 Sheet
            Sheet nodeSheet = workbook.getSheet("知识节点");
            if (nodeSheet == null) {
                result.addWarning("未找到「知识节点」Sheet，请使用系统提供的标准模板");
                return result;
            }

            // 用名称作为 key 存储导入的节点，供关系解析时查找 ID
            Map<String, Long> nameToIdMap = parseNodeSheet(nodeSheet, result);

            // 解析关系 Sheet（可选）
            Sheet relationSheet = workbook.getSheet("知识关系");
            if (relationSheet != null) {
                parseRelationSheet(relationSheet, nameToIdMap, result);
            }

            log.info("Excel 导入完成: 节点={}, 关系={}, 跳过={}",
                    result.createdNodeCount, result.createdRelationCount, result.skippedRowCount);

        } catch (Exception e) {
            log.error("Excel 导入解析失败", e);
            throw new RuntimeException("Excel 解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 解析知识节点 Sheet
     *
     * @param sheet       节点 Sheet
     * @param result      结果统计对象
     * @return 节点名称 → 数据库 ID 的映射
     */
    private Map<String, Long> parseNodeSheet(Sheet sheet, ImportResult result) {
        Map<String, Long> nameToIdMap = new HashMap<>();
        // 跳过表头行（第 0 行）和示例行（第 1 行），从第 2 行（index=2）开始
        int startRow = 2;

        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) {
                continue;
            }

            try {
                String name = getStringValue(row, 0);
                String subject = getStringValue(row, 1);

                // 必填字段校验
                if (name.isBlank() || subject.isBlank()) {
                    result.skippedRowCount++;
                    result.addWarning("第 " + (i + 1) + " 行：「名称」和「学科」为必填项，已跳过");
                    continue;
                }

                // 构建节点视图对象
                KnowledgeNodeView node = new KnowledgeNodeView();
                node.setName(name.trim());
                node.setSubject(subject.trim());
                node.setCategory(getStringValue(row, 2));
                node.setTechnicalDefinition(getStringValue(row, 3));
                node.setIdeologicalValue(getStringValue(row, 4));
                // 节点类型，默认 TECH
                String nodeType = getStringValue(row, 5).toUpperCase().trim();
                node.setDescription(nodeType.equals("IDEO") ? "IDEO" : "TECH");
                node.setIcon(getStringValue(row, 6));
                node.setSubTitle(getStringValue(row, 7));
                // 节点大小，默认 MD
                String nodeSize = getStringValue(row, 8).toUpperCase().trim();
                node.setNodeSize(List.of("LG", "SM").contains(nodeSize) ? nodeSize : "MD");

                // 调用知识图谱服务创建节点
                KnowledgeNodeView created = knowledgeService.createNode(node);
                nameToIdMap.put(name.trim(), created.getId());
                result.createdNodeCount++;

            } catch (Exception e) {
                result.skippedRowCount++;
                result.addWarning("第 " + (i + 1) + " 行：导入失败 - " + e.getMessage());
            }
        }

        return nameToIdMap;
    }

    /**
     * 解析知识关系 Sheet
     */
    private void parseRelationSheet(Sheet sheet, Map<String, Long> nameToIdMap, ImportResult result) {
        int startRow = 2;

        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) {
                continue;
            }

            try {
                String fromName = getStringValue(row, 0).trim();
                String toName = getStringValue(row, 1).trim();
                String relationType = getStringValue(row, 2).toUpperCase().trim();

                if (fromName.isBlank() || toName.isBlank() || relationType.isBlank()) {
                    result.skippedRowCount++;
                    result.addWarning("关系第 " + (i + 1) + " 行：起点、终点、关系类型均为必填，已跳过");
                    continue;
                }

                Long fromId = nameToIdMap.get(fromName);
                Long toId = nameToIdMap.get(toName);

                if (fromId == null) {
                    result.addWarning("关系第 " + (i + 1) + " 行：起点节点「" + fromName + "」未找到，已跳过");
                    result.skippedRowCount++;
                    continue;
                }
                if (toId == null) {
                    result.addWarning("关系第 " + (i + 1) + " 行：终点节点「" + toName + "」未找到，已跳过");
                    result.skippedRowCount++;
                    continue;
                }

                KnowledgeRelation relation = new KnowledgeRelation();
                relation.setFromNodeId(fromId);
                relation.setToNodeId(toId);
                relation.setRelationType(relationType);
                knowledgeService.createRelation(relation);
                result.createdRelationCount++;

            } catch (Exception e) {
                result.skippedRowCount++;
                result.addWarning("关系第 " + (i + 1) + " 行：" + e.getMessage());
            }
        }
    }

    // ============ Excel 样式辅助方法 ============

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createExampleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createNoteStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        return style;
    }

    private void createSheet(Sheet sheet, String[] headers, Object[][] examples,
                             CellStyle headerStyle, CellStyle exampleStyle, CellStyle noteStyle,
                             Workbook workbook) {
        // 说明行
        Row noteRow = sheet.createRow(0);
        Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue("★ 请从第3行开始填写数据。标注 * 的列为必填项。第2行为示例数据（可删除）。");
        noteCell.setCellStyle(noteStyle);

        // 表头行
        Row headerRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 6000);
        }

        // 示例数据行
        for (Object[] exampleRow : examples) {
            Row row = sheet.createRow(sheet.getLastRowNum() + 1);
            for (int j = 0; j < exampleRow.length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(String.valueOf(exampleRow[j]));
                cell.setCellStyle(exampleStyle);
            }
        }
    }

    private void createGuideSheet(Sheet sheet, Workbook workbook) {
        String[] guides = {
                "智教思政 · 知识图谱 Excel 导入模板使用说明",
                "",
                "【节点类型说明】",
                "  TECH - 学科技术知识节点（如：物联网、传感器、人工智能等）",
                "  IDEO - 思政教育节点（如：家国情怀、工匠精神等）",
                "",
                "【关系类型说明】",
                "  TECH_BASE      - 技术基础（A 是 B 的前置知识）",
                "  VALUE_SHOW     - 价值体现（技术节点 → 思政节点，用虚线表示）",
                "  THEORY_SUPPORT - 理论支撑",
                "  PRACTICE_APPLY - 实践应用",
                "",
                "【节点大小说明】",
                "  LG - 大节点（核心/重点知识）",
                "  MD - 中节点（默认，一般知识）",
                "  SM - 小节点（辅助/扩展知识）",
                "",
                "【注意事项】",
                "  1. 所有必填项（标注 *）不可为空，否则该行将被跳过",
                "  2. 关系表中的节点名称需与节点表中的名称完全一致",
                "  3. 系统已有同名节点不会重复创建",
                "  4. 导入完成后，请刷新知识图谱页面查看结果",
        };
        CellStyle bold = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        bold.setFont(font);

        for (int i = 0; i < guides.length; i++) {
            Row row = sheet.createRow(i);
            Cell cell = row.createCell(0);
            cell.setCellValue(guides[i]);
            if (i == 0) {
                cell.setCellStyle(bold);
            }
            sheet.setColumnWidth(0, 18000);
        }
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK && !getStringValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getStringValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        return getStringValue(cell);
    }

    private String getStringValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /**
     * 导入结果统计
     */
    public static class ImportResult {
        public int createdNodeCount = 0;
        public int createdRelationCount = 0;
        public int skippedRowCount = 0;
        public final List<String> warnings = new ArrayList<>();

        public void addWarning(String msg) {
            warnings.add(msg);
        }
    }
}
