package com.smartedu.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档文本抽取服务。
 *
 * <p>
 * 按扩展名分派到不同的抽取器：PDF 用 PDFBox，Office 文档（doc/docx/ppt/pptx/xls/xlsx）
 * 统一走 POI 的 {@link ExtractorFactory}；其余回退到 UTF-8/GBK 纯文本读取。
 *
 * <p>
 * 任何单一文件抽取失败都不会抛出异常而影响上层流水线；调用方应当在得到 null 时
 * 自行决定是否把文件名当作兜底信息。
 */
@Slf4j
@Service
public class DocumentTextExtractor {

    /**
     * 读取文件全文内容，返回 null 表示抽取完全失败。
     *
     * @param filePath 本地文件绝对路径
     * @return 抽取到的文本，失败时为 null
     */
    public String extract(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("File path is invalid: {}", filePath);
            return null;
        }

        String extension = getExtension(filePath);
        try {
            return switch (extension) {
                case "pdf" -> extractPdf(file);
                case "doc", "docx", "ppt", "pptx", "xls", "xlsx" -> extractOffice(file);
                default -> extractPlainText(file);
            };
        } catch (Exception ex) {
            log.warn("Failed to extract text from {}: {}", filePath, ex.getMessage());
            return extractPlainText(file);
        }
    }

    /**
     * 使用 PDFBox 抽取 PDF 文本。对扫描型 PDF（无文字层）会返回空串，调用方需自行处理。
     */
    private String extractPdf(File file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * 使用 Apache POI 通过 {@link ExtractorFactory} 自动选择合适的 extractor，
     * 同时覆盖 OOXML 与老版 OLE2 格式。
     */
    private String extractOffice(File file) throws Exception {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(file)) {
            return extractor.getText();
        }
    }

    /**
     * 非结构化文件：先尝试 UTF-8，失败再尝试 GBK；都失败则返回 null 让上层使用文件名兜底。
     */
    private String extractPlainText(File file) {
        Path path = file.toPath();
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception utf8Ex) {
            try {
                byte[] bytes = Files.readAllBytes(path);
                return new String(bytes, Charset.forName("GBK"));
            } catch (Exception gbkEx) {
                log.warn("Failed to read plain text, fallback to filename inference: {}", file.getPath());
                return null;
            }
        }
    }

    private String getExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= filePath.length() - 1) {
            return "";
        }
        return filePath.substring(dotIndex + 1).toLowerCase();
    }
}
