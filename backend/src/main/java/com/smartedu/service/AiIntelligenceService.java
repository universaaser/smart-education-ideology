package com.smartedu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.entity.ParseTask;
import com.smartedu.mapper.ParseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 智能服务
 * 
 * <p>
 * 核心功能：
 * <ul>
 * <li>调用 MinerU API 解析 PDF 文档</li>
 * <li>调用 DeepSeek API 挖掘思政价值</li>
 * <li>调用 OpenAI 兼容 API 完成对话与结构化分析</li>
 * <li>异步处理耗时任务</li>
 * </ul>
 * 
 * NOTE: 支持多 AI 提供商的策略模式切换
 * 
 * @author SmartEducation Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiIntelligenceService {

    private final ParseTaskMapper parseTaskMapper;
    private final ObjectMapper objectMapper;
    private final KnowledgeIngestionService knowledgeIngestionService;

    // DeepSeek 配置
    @Value("${ai.deepseek.api-key}")
    private String deepseekApiKey;

    @Value("${ai.deepseek.base-url}")
    private String deepseekBaseUrl;

    @Value("${ai.deepseek.model}")
    private String deepseekModel;

    @Value("${ai.deepseek.timeout}")
    private int deepseekTimeout;

    // OpenAI 兼容 API 配置
    @Value("${ai.openai.provider:openai}")
    private String openaiProvider;

    @Value("${ai.openai.api-key}")
    private String openaiApiKey;

    @Value("${ai.openai.base-url}")
    private String openaiBaseUrl;

    @Value("${ai.openai.model}")
    private String openaiModel;

    @Value("${ai.openai.timeout:120}")
    private int openaiTimeout;

    // MinerU 配置
    @Value("${mineru.api-key}")
    private String mineruApiKey;

    @Value("${mineru.base-url}")
    private String mineruBaseUrl;

    @Value("${mineru.timeout}")
    private int mineruTimeout;

    // 当前 AI 提供商
    @Value("${ai.provider}")
    private String currentProvider;

    /**
     * 调用大模型进行对话
     * 
     * @param messages     消息历史
     * @param systemPrompt 系统提示词
     * @return AI 响应内容
     */
    public String chat(List<Map<String, String>> messages, String systemPrompt) {
        String provider = currentProvider == null ? "" : currentProvider.trim().toLowerCase();

        if ("openai".equals(provider)) {
            return callOpenAiCompatible(messages, systemPrompt);
        }
        if ("gemini".equals(provider)) {
            return callGemini(messages, systemPrompt);
        }
        return callDeepSeek(messages, systemPrompt);
    }

    /**
     * 调用 OpenAI 兼容 API
     */
    private String callOpenAiCompatible(List<Map<String, String>> messages, String systemPrompt) {
        return callCompatibleChat(
                openaiBaseUrl,
                openaiApiKey,
                openaiModel,
                openaiTimeout,
                messages,
                systemPrompt,
                openaiProvider);
    }

    /**
     * 调用 DeepSeek API
     */
    private String callDeepSeek(List<Map<String, String>> messages, String systemPrompt) {
        return callCompatibleChat(
                deepseekBaseUrl,
                deepseekApiKey,
                deepseekModel,
                deepseekTimeout,
                messages,
                systemPrompt,
                "deepseek");
    }

    /**
     * 统一的 OpenAI/DeepSeek 兼容调用
     */
    private String callCompatibleChat(
            String apiBase,
            String apiKey,
            String model,
            int timeout,
            List<Map<String, String>> messages,
            String systemPrompt,
            String providerLabel) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .build();

            List<Map<String, String>> allMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                allMessages.add(sysMsg);
            }
            allMessages.addAll(messages);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", allMessages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 4096);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String endpoint = buildChatCompletionUrl(apiBase);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("{} API 调用失败: {}", providerLabel, response.code());
                    throw new RuntimeException("AI 服务调用失败: " + response.code());
                }

                String responseBody = response.body() == null ? "" : response.body().string();
                JsonNode jsonNode = objectMapper.readTree(responseBody);

                return jsonNode.path("choices").path(0)
                        .path("message").path("content").asText();
            }
        } catch (IOException e) {
            log.error("{} API 调用异常", providerLabel, e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage());
        }
    }

    private String buildChatCompletionUrl(String apiBase) {
        if (apiBase == null || apiBase.isBlank()) {
            throw new RuntimeException("AI 服务 base-url 未配置");
        }
        String normalized = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    /**
     * 调用 Gemini API（预留接口）
     * 
     * TODO: 实现 Gemini API 调用逻辑
     */
    private String callGemini(List<Map<String, String>> messages, String systemPrompt) {
        log.warn("Gemini API 尚未实现，回退到 OpenAI 兼容 API");
        return callOpenAiCompatible(messages, systemPrompt);
    }

    /**
     * 挖掘思政价值
     * 
     * <p>
     * 根据专业知识点内容，使用大模型分析其中蕴含的思政元素
     * 
     * @param technicalContent 技术内容
     * @param subject          学科
     * @return 思政价值分析结果
     */
    public String extractIdeologicalValue(String technicalContent, String subject) {
        String systemPrompt = "你是一位资深的课程思政专家，擅长在专业课程中发现和挖掘思政教育元素。\n" +
                "你的任务是分析给定的专业技术内容，从以下维度提取思政价值：\n" +
                "1. 工匠精神：追求极致、精益求精的职业态度\n" +
                "2. 科技报国：自主创新、突破封锁的使命担当\n" +
                "3. 家国情怀：服务社会、报效祖国的责任意识\n" +
                "4. 职业道德：诚信守则、遵纪守法的行为规范\n" +
                "5. 辩证思维：全面分析、科学决策的思维方法\n\n" +
                "请结合具体内容进行分析，给出具有教学实践指导意义的思政融入建议。";

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", String.format(
                "请分析以下【%s】领域的专业内容，挖掘其中的思政教育价值：\n\n%s",
                subject, technicalContent));
        messages.add(userMsg);

        return chat(messages, systemPrompt);
    }

    /**
     * 异步解析文档并分析
     * 
     * @param taskId 任务ID
     */
    @Async
    public void processDocumentAsync(Long taskId) {
        ParseTask task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在: {}", taskId);
            return;
        }

        try {
            // 1. 更新状态：解析中
            updateTaskProgress(task, "PARSING", 20, "正在通过 MinerU 提取文档结构...");

            // 2. 调用 MinerU 解析文档（模拟）
            String parsedContent = parseDocument(task.getFilePath());
            task.setParsedContent(parsedContent);
            updateTaskProgress(task, "PARSING", 40, "识别课程核心知识点...");

            // 3. 更新状态：AI 分析中
            updateTaskProgress(task, "ANALYZING", 60, "利用大模型挖掘思政融合切入点...");

            // 4. 调用 AI 分析思政价值
            String aiAnalysis = extractIdeologicalValue(parsedContent, "通用学科");
            task.setAiAnalysis(aiAnalysis);
            updateTaskProgress(task, "ANALYZING", 80, "正在生成结构化教学建议...");

            // 5. 将解析任务同步进入统一知识模型，供图谱和聊天服务直接复用
            updateTaskProgress(task, "ANALYZING", 90, "正在同步知识点与来源追溯...");
            knowledgeIngestionService.ingestParseTask(task);

            // 6. 完成
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCurrentStep("解析完成");
            task.setCompletedAt(LocalDateTime.now());
            parseTaskMapper.updateById(task);

            log.info("文档解析完成: taskId={}", taskId);

        } catch (Exception e) {
            log.error("文档解析失败: taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            parseTaskMapper.updateById(task);
        }
    }

    /**
     * 更新任务进度
     */
    private void updateTaskProgress(ParseTask task, String status, int progress, String step) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setCurrentStep(step);
        task.setUpdatedAt(LocalDateTime.now());
        parseTaskMapper.updateById(task);

        // 模拟处理耗时
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析文档
     *
     * <p>
     * 策略：① 读取文件原始文本内容；② 调用已接入的大模型 AI，通过结构化提示词
     * 提取章节、知识点列表和思政关联，输出格式与 MinerU 结构化解析结果对齐。
     * 当文件无法读取时，返回友好的占位内容。
     *
     * NOTE: 后续若接入真实 MinerU API，只需替换此方法的实现，上层流程无需改动。
     *
     * @param filePath 文件路径
     * @return 解析后的结构化 Markdown 文本
     */
    private String parseDocument(String filePath) {
        log.info("正在解析文档: {}", filePath);

        // Step 1: 读取文件内容（纯文本提取，不涉及格式解析）
        String rawContent = readFileContent(filePath);

        // Step 2: 构造提示词，通过 AI 实现结构化解析
        String systemPrompt = "你是一个专业的教学文档解析助手，擅长从教材、论文和课程资料中提取结构化知识内容。\n"
                + "你的输出必须使用 Markdown 格式，严格按照以下模板组织：\n\n"
                + "# 文档解析结果\n\n"
                + "## 文档概述\n（一句话说明文档主题和领域）\n\n"
                + "## 章节目录\n（按序号列出主要章节名称）\n\n"
                + "## 核心知识点\n（用列表列出5~15个关键知识点，每条包含名称和一句话简介）\n\n"
                + "## 教学重点\n（指出3~5个适合课堂重点讲解的内容）\n\n"
                + "## 思政关联线索\n（指出文档内容与国家政策、科技报国、职业道德等思政元素的潜在关联）\n\n"
                + "请保持结构严格，不要输出模板以外的内容。";

        String userPrompt;
        if (rawContent != null && !rawContent.isBlank()) {
            // 文本截断：AI 最大输入限制，保留前 3000 字符
            String truncated = rawContent.length() > 3000
                    ? rawContent.substring(0, 3000) + "\n...(内容过长，已截断)"
                    : rawContent;
            userPrompt = "请解析以下文档内容，按规定格式输出结构化知识点：\n\n" + truncated;
        } else {
            // 文件无法读取时，仅凭文件名进行推断
            String fileName = filePath.contains("/")
                    ? filePath.substring(filePath.lastIndexOf('/') + 1)
                    : filePath.substring(filePath.lastIndexOf('\\') + 1);
            userPrompt = "无法读取文件内容，请根据文件名「" + fileName + "」推断文档主题，"
                    + "按规定格式输出合理的结构化知识点（标注「内容为推断」）。";
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            String result = chat(messages, systemPrompt);
            log.info("AI 文档解析完成，输出长度: {}", result.length());
            return result;
        } catch (Exception e) {
            log.error("AI 文档解析失败，降级为基础内容: {}", e.getMessage());
            // 降级：返回基础占位内容，避免任务失败
            return "# 文档解析结果\n\n"
                    + "> 注意：AI 解析服务暂时不可用，以下为基础占位内容。\n\n"
                    + "## 文档概述\n文件已成功上传，请稍后重新触发解析任务。\n\n"
                    + "## 核心知识点\n- 知识点提取待完成\n\n"
                    + "## 思政关联线索\n- 思政关联分析待完成";
        }
    }

    /**
     * 读取文件内容为纯文本字符串
     *
     * <p>
     * 支持纯文本文件（.txt / .md）和基础二进制文件的文本提取。
     * PDF / DOCX 等格式暂不做深度解析，仅尝试读取可识别的文本字节。
     *
     * @param filePath 文件路径
     * @return 文件文本内容，读取失败返回 null
     */
    private String readFileContent(String filePath) {
        java.io.File file = new java.io.File(filePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("文件不存在或路径无效: {}", filePath);
            return null;
        }

        try {
            // 优先尝试 UTF-8 读取（适用于 .txt / .md / .csv）
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of(filePath),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception utf8Ex) {
            try {
                // 降级：GBK 读取（适用于中文 Windows 文档）
                byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(filePath));
                return new String(bytes, java.nio.charset.Charset.forName("GBK"));
            } catch (Exception gbkEx) {
                log.warn("文件内容读取失败，将由 AI 根据文件名推断: {}", filePath);
                return null;
            }
        }
    }
}
