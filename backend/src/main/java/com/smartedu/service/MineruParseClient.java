package com.smartedu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.dto.mineru.MineruContentBlockDto;
import com.smartedu.dto.mineru.MineruOutlineNodeDto;
import com.smartedu.dto.mineru.MineruStatsDto;
import com.smartedu.dto.mineru.MineruStructuredContentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU 文档解析客户端。
 *
 * <p>
 * 走 MinerU v4 "精准解析 / 本地文件批量上传" 链路：
 * <ol>
 *   <li>POST {@code /file-urls/batch} 申请上传链接；</li>
 *   <li>PUT 文件到返回的链接（无 Content-Type）；</li>
 *   <li>GET {@code /extract-results/batch/{batch_id}} 轮询，直到 state=done；</li>
 *   <li>下载 full_zip_url，从 zip 中取 {@code full.md} 作为 markdown 输出，并解析
 *       {@code content_list.json} 生成结构化内容；图片资源写入本地目录供静态服务。</li>
 * </ol>
 *
 * <p>
 * 任何阶段失败都抛 {@link MineruParseException}，上层决定降级策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MineruParseClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final long POLL_INTERVAL_MS = 3000L;
    private static final int ZIP_MAX_BYTES = 64 * 1024 * 1024;

    private final ObjectMapper objectMapper;

    @Value("${mineru.enabled:true}")
    private boolean enabled;

    @Value("${mineru.api-key:}")
    private String apiKey;

    @Value("${mineru.base-url:https://mineru.net/api/v4}")
    private String baseUrl;

    @Value("${mineru.model-version:vlm}")
    private String modelVersion;

    @Value("${mineru.language:ch}")
    private String language;

    @Value("${mineru.enable-formula:true}")
    private boolean enableFormula;

    @Value("${mineru.enable-table:true}")
    private boolean enableTable;

    @Value("${mineru.is-ocr:false}")
    private boolean isOcr;

    /** 整体超时（秒），覆盖从提交到拿到 zip 的全流程。 */
    @Value("${mineru.timeout:300}")
    private int totalTimeoutSeconds;

    /** HTTP 单请求超时（秒）。 */
    @Value("${mineru.http-timeout:60}")
    private int httpTimeoutSeconds;

    /** MinerU 资源落盘目录（相对或绝对）。前端通过静态资源路径访问。 */
    @Value("${mineru.assets-path:./uploads/mineru-assets}")
    private String assetsPath;

    /** 前端访问 MinerU 资源的 URL 前缀，与 WebMvcConfig 保持一致。 */
    @Value("${mineru.asset-url-prefix:/api/upload/mineru-assets}")
    private String assetUrlPrefix;

    public boolean isAvailable() {
        return enabled
                && apiKey != null
                && !apiKey.isBlank()
                && baseUrl != null
                && !baseUrl.isBlank();
    }

    /**
     * 解析本地文件并返回结构化结果。调用方需自行捕获 {@link MineruParseException} 并决定降级。
     */
    public MineruParseResult parseLocalFile(String filePath, String originalFileName) {
        if (!isAvailable()) {
            throw new MineruParseException("MinerU is not configured");
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new MineruParseException("File not found for MinerU parse: " + filePath);
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(httpTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(httpTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(httpTimeoutSeconds, TimeUnit.SECONDS)
                .build();

        String safeName = originalFileName != null && !originalFileName.isBlank()
                ? originalFileName
                : file.getName();
        String dataId = "task-" + System.currentTimeMillis();

        BatchApplyResult applied = applyUploadUrl(client, safeName, dataId);
        uploadFile(client, applied.uploadUrl, file);
        ExtractResult extractResult = pollBatchResult(client, applied.batchId, safeName);
        byte[] zipBytes = downloadZip(client, extractResult.fullZipUrl);

        MineruParseResult result = new MineruParseResult();
        result.batchId = applied.batchId;
        result.zipUrl = extractResult.fullZipUrl;
        result.modelVersion = modelVersion;
        extractZipAndBuildResult(zipBytes, applied.batchId, result);
        return result;
    }

    // ---------------------------------------------------------------------
    // MinerU HTTP 阶段
    // ---------------------------------------------------------------------

    private BatchApplyResult applyUploadUrl(OkHttpClient client, String fileName, String dataId) {
        Map<String, Object> fileSpec = new HashMap<>();
        fileSpec.put("name", fileName);
        fileSpec.put("data_id", dataId);
        fileSpec.put("is_ocr", isOcr);
        fileSpec.put("enable_formula", enableFormula);
        fileSpec.put("enable_table", enableTable);
        if (language != null && !language.isBlank()) {
            fileSpec.put("language", language);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("files", List.of(fileSpec));
        body.put("model_version", modelVersion);

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new MineruParseException("Failed to build MinerU apply body", ex);
        }

        Request req = new Request.Builder()
                .url(joinUrl(baseUrl, "/file-urls/batch"))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "*/*")
                .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String text = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new MineruParseException("MinerU apply upload failed: HTTP " + resp.code() + " body=" + text);
            }
            JsonNode root = objectMapper.readTree(text);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new MineruParseException("MinerU apply upload rejected: " + root.path("msg").asText("unknown"));
            }
            JsonNode data = root.path("data");
            String batchId = data.path("batch_id").asText(null);
            JsonNode urls = data.path("file_urls");
            if (batchId == null || urls == null || !urls.isArray() || urls.isEmpty()) {
                throw new MineruParseException("MinerU apply upload missing batch_id/file_urls");
            }
            BatchApplyResult r = new BatchApplyResult();
            r.batchId = batchId;
            r.uploadUrl = urls.get(0).asText();
            log.info("MinerU apply upload ok: batchId={}", batchId);
            return r;
        } catch (IOException ex) {
            throw new MineruParseException("MinerU apply upload IO error", ex);
        }
    }

    private void uploadFile(OkHttpClient client, String uploadUrl, File file) {
        // 官方示例强调：PUT 上传时不要带 Content-Type。RequestBody.create(File, null) 不会设置。
        RequestBody body = RequestBody.create(file, null);
        Request req = new Request.Builder()
                .url(uploadUrl)
                .put(body)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String text = resp.body() != null ? resp.body().string() : "";
                throw new MineruParseException("MinerU PUT upload failed: HTTP " + resp.code() + " body=" + text);
            }
            log.info("MinerU PUT upload ok: {} bytes", file.length());
        } catch (IOException ex) {
            throw new MineruParseException("MinerU PUT upload IO error", ex);
        }
    }

    private ExtractResult pollBatchResult(OkHttpClient client, String batchId, String fileName) {
        long deadline = System.currentTimeMillis() + totalTimeoutSeconds * 1000L;
        String url = joinUrl(baseUrl, "/extract-results/batch/" + batchId);
        while (System.currentTimeMillis() < deadline) {
            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Accept", "*/*")
                    .get()
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                String text = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    throw new MineruParseException("MinerU poll failed: HTTP " + resp.code() + " body=" + text);
                }
                JsonNode root = objectMapper.readTree(text);
                if (root.path("code").asInt(-1) != 0) {
                    throw new MineruParseException("MinerU poll rejected: " + root.path("msg").asText("unknown"));
                }
                JsonNode arr = root.path("data").path("extract_result");
                if (arr == null || !arr.isArray() || arr.isEmpty()) {
                    throw new MineruParseException("MinerU poll empty extract_result");
                }
                // 单文件场景：取第一个即可；兼容批量场景按 file_name 匹配。
                JsonNode entry = null;
                for (JsonNode node : arr) {
                    if (fileName.equals(node.path("file_name").asText())) {
                        entry = node;
                        break;
                    }
                }
                if (entry == null) {
                    entry = arr.get(0);
                }
                String state = entry.path("state").asText("");
                if ("done".equalsIgnoreCase(state)) {
                    String zip = entry.path("full_zip_url").asText(null);
                    if (zip == null || zip.isBlank()) {
                        throw new MineruParseException("MinerU done but full_zip_url missing");
                    }
                    ExtractResult r = new ExtractResult();
                    r.fullZipUrl = zip;
                    return r;
                }
                if ("failed".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state)) {
                    throw new MineruParseException("MinerU extract failed: " + entry.path("err_msg").asText(""));
                }
                // 其他状态（pending/running）继续轮询
            } catch (IOException ex) {
                throw new MineruParseException("MinerU poll IO error", ex);
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new MineruParseException("MinerU poll interrupted", ie);
            }
        }
        throw new MineruParseException("MinerU poll timeout after " + totalTimeoutSeconds + "s");
    }

    private byte[] downloadZip(OkHttpClient client, String zipUrl) {
        Request req = new Request.Builder().url(zipUrl).get().build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new MineruParseException("MinerU zip download failed: HTTP " + resp.code());
            }
            ResponseBody body = resp.body();
            if (body == null) {
                throw new MineruParseException("MinerU zip download empty body");
            }
            byte[] zipBytes = body.bytes();
            if (zipBytes.length == 0) {
                throw new MineruParseException("MinerU zip download zero bytes");
            }
            if (zipBytes.length > ZIP_MAX_BYTES) {
                log.warn("MinerU zip exceeds size cap: {} bytes", zipBytes.length);
            }
            return zipBytes;
        } catch (IOException ex) {
            throw new MineruParseException("MinerU zip download IO error", ex);
        }
    }

    // ---------------------------------------------------------------------
    // zip 解析 + 结构化
    // ---------------------------------------------------------------------

    /**
     * 解压 zip，把图片落到本地目录，解析 content_list.json 为结构化数据，同时装配 markdown。
     */
    private void extractZipAndBuildResult(byte[] zipBytes, String batchId, MineruParseResult result) {
        String fullMd = null;
        String anyMd = null;
        String contentListJson = null;
        Path assetsRoot;
        try {
            assetsRoot = Paths.get(assetsPath).toAbsolutePath().normalize();
            Files.createDirectories(assetsRoot);
        } catch (IOException ex) {
            throw new MineruParseException("Failed to create MinerU assets directory", ex);
        }
        Path batchDir = assetsRoot.resolve(batchId);
        try {
            Files.createDirectories(batchDir);
        } catch (IOException ex) {
            throw new MineruParseException("Failed to create MinerU batch directory", ex);
        }

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String rawName = entry.getName();
                String name = rawName.toLowerCase(Locale.ROOT);
                if (name.endsWith("full.md")) {
                    fullMd = readEntryAsString(zis);
                } else if (anyMd == null && name.endsWith(".md")) {
                    anyMd = readEntryAsString(zis);
                } else if (contentListJson == null && name.endsWith("content_list.json")) {
                    contentListJson = readEntryAsString(zis);
                } else if (isAssetEntry(name)) {
                    // 把图片/附件落到 batchDir 下，保留相对路径。
                    writeAsset(batchDir, rawName, zis);
                }
                // 其他文件（layout.json / model.json / middle.json / span.pdf 等）暂不处理。
            }
        } catch (IOException ex) {
            throw new MineruParseException("Failed to read MinerU zip", ex);
        }

        String markdown = fullMd != null && !fullMd.isBlank() ? fullMd
                : (anyMd != null && !anyMd.isBlank() ? anyMd : null);

        MineruStructuredContentDto structured = new MineruStructuredContentDto();
        structured.setBatchId(batchId);
        structured.setModelVersion(modelVersion);
        String assetBaseUrl = ensureTrailingSlash(assetUrlPrefix) + batchId + "/";
        structured.setAssetBaseUrl(assetBaseUrl);
        buildStructuredContent(contentListJson, assetBaseUrl, structured);

        // 如果既没有 markdown 也没有 content_list，则认为失败。
        if (markdown == null && structured.getBlocks().isEmpty()) {
            throw new MineruParseException("MinerU zip has no markdown nor content_list");
        }

        // 缺 markdown 时，用 blocks 兜底合成一份，保证前端 Markdown Tab 始终有内容。
        if (markdown == null) {
            markdown = synthesizeMarkdownFromBlocks(structured.getBlocks());
        }

        result.markdown = markdown;
        result.structuredContent = structured;
    }

    private boolean isAssetEntry(String lowerName) {
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png") || lowerName.endsWith(".gif")
                || lowerName.endsWith(".webp") || lowerName.endsWith(".svg");
    }

    private void writeAsset(Path batchDir, String rawName, InputStream content) throws IOException {
        // 规避 zip slip：解析后的绝对路径必须在 batchDir 之下。
        Path target = batchDir.resolve(rawName).normalize();
        if (!target.startsWith(batchDir)) {
            log.warn("Skip zip entry outside batch dir: {}", rawName);
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private String readEntryAsString(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = zis.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    /**
     * 把 content_list.json 转为 blocks + outline + stats + 分类索引。
     * 不抛异常：JSON 不可解析时只记录日志，structured 保持为空集合，上层可根据 markdown 兜底。
     */
    private void buildStructuredContent(
            String contentListJson,
            String assetBaseUrl,
            MineruStructuredContentDto structured) {
        if (contentListJson == null || contentListJson.isBlank()) {
            structured.setStats(new MineruStatsDto());
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(contentListJson);
        } catch (Exception ex) {
            log.warn("MinerU content_list.json unreadable: {}", ex.getMessage());
            structured.setStats(new MineruStatsDto());
            return;
        }
        if (!root.isArray()) {
            structured.setStats(new MineruStatsDto());
            return;
        }

        List<MineruContentBlockDto> blocks = new ArrayList<>();
        List<MineruContentBlockDto> images = new ArrayList<>();
        List<MineruContentBlockDto> tables = new ArrayList<>();
        List<MineruContentBlockDto> equations = new ArrayList<>();
        MineruStatsDto stats = new MineruStatsDto();
        int maxPage = -1;
        int wordCount = 0;

        int index = 0;
        for (JsonNode item : root) {
            MineruContentBlockDto block = new MineruContentBlockDto();
            block.setIndex(index++);
            String type = item.path("type").asText("text");
            block.setType(type);
            if (item.has("text")) {
                block.setText(item.path("text").asText(""));
            }
            if (item.has("text_level")) {
                block.setTextLevel(item.path("text_level").asInt(0));
            }
            if (item.has("text_format")) {
                block.setTextFormat(item.path("text_format").asText(null));
            }
            if (item.has("sub_type")) {
                block.setSubType(item.path("sub_type").asText(null));
            }
            if (item.has("page_idx")) {
                int pageIdx = item.path("page_idx").asInt(-1);
                block.setPageIdx(pageIdx);
                if (pageIdx > maxPage) maxPage = pageIdx;
            }
            if (item.has("bbox") && item.path("bbox").isArray()) {
                List<Number> bbox = new ArrayList<>();
                for (JsonNode coord : item.path("bbox")) {
                    bbox.add(coord.isIntegralNumber() ? coord.longValue() : coord.doubleValue());
                }
                block.setBbox(bbox);
            }
            if (item.has("img_path")) {
                String rel = item.path("img_path").asText("");
                if (!rel.isBlank()) {
                    block.setImagePath(rel);
                    block.setImageUrl(assetBaseUrl + rel.replace('\\', '/'));
                }
            }
            block.setImageCaption(readStringArray(item, "image_caption"));
            block.setImageFootnote(readStringArray(item, "image_footnote"));
            block.setTableCaption(readStringArray(item, "table_caption"));
            block.setTableFootnote(readStringArray(item, "table_footnote"));
            if (item.has("table_body")) {
                block.setTableBody(item.path("table_body").asText(null));
            }

            blocks.add(block);

            // 统计信息
            switch (type) {
                case "image", "chart" -> {
                    images.add(block);
                    stats.setImageCount(stats.getImageCount() + 1);
                }
                case "table" -> {
                    tables.add(block);
                    stats.setTableCount(stats.getTableCount() + 1);
                }
                case "equation" -> {
                    equations.add(block);
                    stats.setEquationCount(stats.getEquationCount() + 1);
                }
                case "list" -> stats.setListCount(stats.getListCount() + 1);
                case "code" -> stats.setCodeCount(stats.getCodeCount() + 1);
                case "text" -> {
                    Integer lvl = block.getTextLevel();
                    if (lvl != null && lvl >= 1) {
                        stats.setHeadingCount(stats.getHeadingCount() + 1);
                    } else {
                        stats.setParagraphCount(stats.getParagraphCount() + 1);
                    }
                    if (block.getText() != null) {
                        wordCount += block.getText().length();
                    }
                }
                default -> {
                    // page_number/footer/aside_text 等不计入主要统计
                }
            }
        }
        stats.setPageCount(maxPage + 1);
        stats.setWordCount(wordCount);

        structured.setBlocks(blocks);
        structured.setImages(images);
        structured.setTables(tables);
        structured.setEquations(equations);
        structured.setStats(stats);
        structured.setOutline(buildOutline(blocks));
    }

    private List<String> readStringArray(JsonNode node, String field) {
        JsonNode arr = node.path(field);
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (JsonNode v : arr) {
            String s = v.asText("");
            if (!s.isBlank()) list.add(s);
        }
        return list.isEmpty() ? null : list;
    }

    /**
     * 由标题块折叠出大纲树。遇到更浅级别时回溯栈，相同/更深级别继续作为子节点。
     */
    private List<MineruOutlineNodeDto> buildOutline(List<MineruContentBlockDto> blocks) {
        List<MineruOutlineNodeDto> roots = new ArrayList<>();
        Deque<MineruOutlineNodeDto> stack = new ArrayDeque<>();
        for (MineruContentBlockDto block : blocks) {
            Integer lvl = block.getTextLevel();
            if (!"text".equals(block.getType()) || lvl == null || lvl < 1) continue;
            MineruOutlineNodeDto node = new MineruOutlineNodeDto();
            node.setTitle(block.getText() == null ? "" : block.getText());
            node.setLevel(lvl);
            node.setBlockIndex(block.getIndex());
            node.setPageIdx(block.getPageIdx() == null ? -1 : block.getPageIdx());

            while (!stack.isEmpty() && stack.peek().getLevel() >= lvl) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.peek().getChildren().add(node);
            }
            stack.push(node);
        }
        return roots;
    }

    /**
     * zip 没有 markdown 时用 blocks 拼一份兜底纯文本。
     */
    private String synthesizeMarkdownFromBlocks(List<MineruContentBlockDto> blocks) {
        StringBuilder sb = new StringBuilder();
        for (MineruContentBlockDto b : blocks) {
            String type = b.getType();
            String text = b.getText();
            if ("text".equals(type) && b.getTextLevel() != null && b.getTextLevel() >= 1) {
                int lvl = Math.max(1, Math.min(6, b.getTextLevel()));
                sb.append("#".repeat(lvl)).append(' ')
                        .append(text == null ? "" : text).append("\n\n");
            } else if ("text".equals(type) && text != null && !text.isBlank()) {
                sb.append(text).append("\n\n");
            } else if ("image".equals(type) && b.getImageUrl() != null) {
                sb.append("![")
                        .append(joinCaption(b.getImageCaption()))
                        .append("](")
                        .append(b.getImageUrl()).append(")\n\n");
            } else if ("table".equals(type) && b.getTableBody() != null) {
                sb.append(b.getTableBody()).append("\n\n");
            } else if ("equation".equals(type) && text != null) {
                sb.append(text).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String joinCaption(List<String> captions) {
        if (captions == null || captions.isEmpty()) return "";
        return String.join(" ", captions);
    }

    private static String ensureTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return "/";
        return s.endsWith("/") ? s : s + "/";
    }

    private static String joinUrl(String base, String path) {
        if (base.endsWith("/") && path.startsWith("/")) {
            return base + path.substring(1);
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    /** MinerU 返回的结构化结果。 */
    public static class MineruParseResult {
        public String markdown;
        public String batchId;
        public String zipUrl;
        public String modelVersion;
        public MineruStructuredContentDto structuredContent;
    }

    private static class BatchApplyResult {
        String batchId;
        String uploadUrl;
    }

    private static class ExtractResult {
        String fullZipUrl;
    }

    /** MinerU 链路异常统一类型，上层据此决定是否降级。 */
    public static class MineruParseException extends RuntimeException {
        public MineruParseException(String message) {
            super(message);
        }

        public MineruParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
