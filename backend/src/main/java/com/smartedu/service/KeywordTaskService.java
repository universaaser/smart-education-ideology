package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.KeywordTaskCreateRequestDto;
import com.smartedu.dto.KeywordTaskDto;
import com.smartedu.dto.KeywordTaskItemDto;
import com.smartedu.entity.KeywordTask;
import com.smartedu.entity.KeywordTaskItem;
import com.smartedu.entity.Resource;
import com.smartedu.mapper.KeywordTaskItemMapper;
import com.smartedu.mapper.KeywordTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KeywordTaskService {

    private final KeywordTaskMapper keywordTaskMapper;
    private final KeywordTaskItemMapper keywordTaskItemMapper;
    private final ResourceService resourceService;
    private final AiIntelligenceService aiIntelligenceService;

    public List<KeywordTaskDto> listTasks(Long courseId) {
        LambdaQueryWrapper<KeywordTask> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(KeywordTask::getCourseId, courseId);
        }
        wrapper.orderByDesc(KeywordTask::getCreatedAt).orderByDesc(KeywordTask::getId);
        return keywordTaskMapper.selectList(wrapper).stream()
                .map(task -> toDto(task, false))
                .toList();
    }

    public KeywordTaskDto getTask(Long id) {
        KeywordTask task = keywordTaskMapper.selectById(id);
        return task == null ? null : toDto(task, true);
    }

    @Transactional
    public KeywordTaskDto createTask(KeywordTaskCreateRequestDto request) {
        KeywordTask task = new KeywordTask();
        task.setCourseId(request.getCourseId());
        task.setCreatorId(request.getCreatorId());
        task.setKeywords(String.join(",", normalizeKeywords(request.getKeywords())));
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        keywordTaskMapper.insert(task);
        return toDto(task, true);
    }

    @Transactional
    public KeywordTaskDto runTask(Long id) {
        KeywordTask task = keywordTaskMapper.selectById(id);
        if (task == null) {
            return null;
        }
        task.setStatus("RUNNING");
        task.setErrorSummary("");
        task.setUpdatedAt(LocalDateTime.now());
        keywordTaskMapper.updateById(task);

        keywordTaskItemMapper.delete(new LambdaQueryWrapper<KeywordTaskItem>().eq(KeywordTaskItem::getTaskId, id));
        int created = 0;
        try {
            for (String keyword : parseKeywords(task.getKeywords())) {
                List<Resource> matches = resourceService.searchForChatContext(keyword, 3);
                if (matches.isEmpty()) {
                    insertSkippedItem(task.getId(), keyword);
                    continue;
                }
                for (Resource resource : matches) {
                    insertResultItem(task.getId(), keyword, resource);
                    created++;
                }
            }
            task.setStatus("DONE");
            task.setResultSummary(created == 0 ? "No approved resources matched" : "Generated " + created + " keyword result items");
            task.setFinishedAt(LocalDateTime.now());
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorSummary(trim(e.getMessage(), 500));
            task.setFinishedAt(LocalDateTime.now());
        }
        task.setUpdatedAt(LocalDateTime.now());
        keywordTaskMapper.updateById(task);
        return toDto(task, true);
    }

    @Transactional
    public Resource acceptItem(Long taskId, Long itemId) {
        KeywordTaskItem item = keywordTaskItemMapper.selectById(itemId);
        if (item == null || !taskId.equals(item.getTaskId()) || "ACCEPTED".equals(item.getStatus())) {
            return null;
        }
        KeywordTask task = keywordTaskMapper.selectById(taskId);
        if (task == null || item.getTitle() == null || item.getTitle().isBlank()) {
            return null;
        }

        Resource existing = resourceService.getBySourceUrl(item.getSourceUrl());
        Resource accepted;
        if (existing != null) {
            accepted = existing;
        } else {
            Resource resource = new Resource();
            resource.setTitle(item.getTitle());
            resource.setSource("Keyword Task");
            resource.setSourceUrl(item.getSourceUrl());
            resource.setCategory("Keyword Collection");
            resource.setContent(item.getExcerpt());
            resource.setIdeologySummary(item.getAiSummary());
            resource.setTags(item.getIdeologyTags() == null || item.getIdeologyTags().isBlank() ? "[]" : item.getIdeologyTags());
            resource.setSyncStatus("SYNCED");
            resource.setReviewStatus("APPROVED");
            resource.setCreatorId(task.getCreatorId());
            accepted = resourceService.createResource(resource);
        }

        item.setStatus("ACCEPTED");
        item.setResourceId(accepted.getId());
        item.setUpdatedAt(LocalDateTime.now());
        keywordTaskItemMapper.updateById(item);
        return accepted;
    }

    public List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String keyword : keywords) {
            String value = trim(keyword, 80);
            if (!value.isBlank() && !normalized.contains(value)) {
                normalized.add(value);
            }
            if (normalized.size() >= 10) {
                break;
            }
        }
        return normalized;
    }

    private void insertResultItem(Long taskId, String keyword, Resource resource) {
        KeywordTaskItem item = new KeywordTaskItem();
        item.setTaskId(taskId);
        item.setKeyword(keyword);
        item.setTitle(trim(resource.getTitle(), 200));
        item.setSourceUrl(trim(resource.getSourceUrl(), 500));
        item.setExcerpt(trim(firstNonBlank(resource.getContent(), resource.getIdeologySummary()), 1000));
        item.setAiSummary(buildAiSummary(keyword, resource));
        item.setIdeologyTags(firstNonBlank(resource.getTags(), "[]"));
        item.setStatus("PENDING");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        keywordTaskItemMapper.insert(item);
    }

    private void insertSkippedItem(Long taskId, String keyword) {
        KeywordTaskItem item = new KeywordTaskItem();
        item.setTaskId(taskId);
        item.setKeyword(keyword);
        item.setTitle("No approved resource matched: " + keyword);
        item.setSourceUrl("");
        item.setExcerpt("");
        item.setAiSummary("No approved resource matched this keyword.");
        item.setIdeologyTags("[]");
        item.setStatus("SKIPPED");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        keywordTaskItemMapper.insert(item);
    }

    private String buildAiSummary(String keyword, Resource resource) {
        String excerpt = trim(firstNonBlank(resource.getContent(), resource.getIdeologySummary()), 1200);
        try {
            String answer = aiIntelligenceService.chatForTask(
                    AiIntelligenceService.TASK_CRAWL,
                    List.of(Map.of("role", "user", "content", "Keyword: " + keyword + "\nTitle: " + resource.getTitle() + "\nExcerpt: " + excerpt)),
                    "Summarize the teaching value and ideology integration point in 80 words or fewer.");
            if (answer != null && !answer.isBlank()) {
                return trim(answer, 1000);
            }
        } catch (Exception ignored) {
        }
        return trim(firstNonBlank(resource.getIdeologySummary(), excerpt), 1000);
    }

    private KeywordTaskDto toDto(KeywordTask task, boolean includeItems) {
        List<KeywordTaskItemDto> items = includeItems
                ? keywordTaskItemMapper.selectList(new LambdaQueryWrapper<KeywordTaskItem>()
                        .eq(KeywordTaskItem::getTaskId, task.getId())
                        .orderByAsc(KeywordTaskItem::getId)).stream().map(this::toItemDto).toList()
                : List.of();
        return new KeywordTaskDto(
                task.getId(),
                task.getCourseId(),
                task.getCreatorId(),
                parseKeywords(task.getKeywords()),
                task.getStatus(),
                task.getResultSummary(),
                task.getErrorSummary(),
                task.getFinishedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                items);
    }

    private KeywordTaskItemDto toItemDto(KeywordTaskItem item) {
        return new KeywordTaskItemDto(
                item.getId(),
                item.getTaskId(),
                item.getKeyword(),
                item.getTitle(),
                item.getSourceUrl(),
                item.getExcerpt(),
                item.getAiSummary(),
                item.getIdeologyTags(),
                item.getStatus(),
                item.getResourceId(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    private String trim(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
