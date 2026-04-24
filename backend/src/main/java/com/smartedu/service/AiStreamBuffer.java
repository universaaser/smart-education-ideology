package com.smartedu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档解析过程中用于捕获 LLM 实时输出的内存缓冲区。
 *
 * <p>
 * 设计要点：
 * <ul>
 *   <li>按 taskId 维度保存独立 {@link StringBuilder}，前端通过游标增量拉取新内容。</li>
 *   <li>使用 {@link ThreadLocal} 记录"当前异步任务"，避免在调用链每一层都显式透传 taskId。</li>
 *   <li>单任务缓冲区有最大容量上限，超过时丢弃最旧的一段并追加截断标记，防止长任务耗尽内存。</li>
 * </ul>
 */
@Slf4j
@Service
public class AiStreamBuffer {

    /** 单任务缓冲区上限（字符数）。超过后会丢弃最旧 25% 并追加截断标记。 */
    private static final int MAX_BUFFER_SIZE = 64 * 1024;

    /** 达到上限时为避免频繁扩缩容，一次性丢弃的长度比例。 */
    private static final int TRIM_SIZE = MAX_BUFFER_SIZE / 4;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** taskId → 实时日志缓冲区。ConcurrentHashMap 保证多线程并发读写安全。 */
    private final Map<Long, StringBuilder> buffers = new ConcurrentHashMap<>();

    /** 当前线程上下文绑定的 taskId，异步任务开始时设置、结束时清除。 */
    private final ThreadLocal<Long> currentTaskId = new ThreadLocal<>();

    /**
     * 在异步任务入口绑定 taskId，同时清空历史缓冲区。
     */
    public void beginTask(Long taskId) {
        if (taskId == null) {
            return;
        }
        currentTaskId.set(taskId);
        buffers.put(taskId, new StringBuilder());
        appendLine(taskId, "[" + now() + "] Pipeline started for taskId=" + taskId);
    }

    /**
     * 异步任务结束时调用，仅清理 ThreadLocal，保留缓冲内容供前端最后一次拉取。
     */
    public void endTask() {
        Long taskId = currentTaskId.get();
        if (taskId != null) {
            appendLine(taskId, "[" + now() + "] Pipeline finished for taskId=" + taskId);
        }
        currentTaskId.remove();
    }

    /**
     * 标记一个新的流水线阶段（document-structure / knowledge-point-extraction 等）。
     */
    public void appendStage(String stageLabel) {
        Long taskId = currentTaskId.get();
        if (taskId == null) {
            return;
        }
        appendLine(taskId, "\n[" + now() + "] >>> " + stageLabel);
    }

    /**
     * 追加 LLM 流式输出的一段文本；当前线程未绑定 taskId 时静默丢弃。
     */
    public void appendChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        Long taskId = currentTaskId.get();
        if (taskId == null) {
            return;
        }
        StringBuilder builder = buffers.get(taskId);
        if (builder == null) {
            return;
        }
        synchronized (builder) {
            builder.append(chunk);
            trimIfOversized(builder);
        }
    }

    /**
     * 前端增量拉取接口。
     *
     * @param taskId 任务 id
     * @param offset 上一次读取的游标；0 表示从头拉
     * @return 游标之后的新内容与当前游标位置
     */
    public LiveLogSlice read(Long taskId, int offset) {
        StringBuilder builder = buffers.get(taskId);
        if (builder == null) {
            return new LiveLogSlice("", 0);
        }
        synchronized (builder) {
            int total = builder.length();
            int safeOffset = Math.max(0, Math.min(offset, total));
            String slice = builder.substring(safeOffset, total);
            return new LiveLogSlice(slice, total);
        }
    }

    /**
     * 丢弃某个任务的缓冲区（例如旧任务太多时可以由外部手动清理）。当前未被自动调用。
     */
    public void discard(Long taskId) {
        buffers.remove(taskId);
    }

    private void appendLine(Long taskId, String line) {
        StringBuilder builder = buffers.get(taskId);
        if (builder == null) {
            return;
        }
        synchronized (builder) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(line);
            if (!line.endsWith("\n")) {
                builder.append('\n');
            }
            trimIfOversized(builder);
        }
    }

    private void trimIfOversized(StringBuilder builder) {
        if (builder.length() <= MAX_BUFFER_SIZE) {
            return;
        }
        builder.delete(0, TRIM_SIZE);
        builder.insert(0, "...[truncated older output]...\n");
    }

    private String now() {
        return LocalDateTime.now().format(TIMESTAMP);
    }

    /**
     * 增量拉取返回体：content 为新增文本，cursor 为最新的总长度。
     */
    public record LiveLogSlice(String content, int cursor) {
    }
}
