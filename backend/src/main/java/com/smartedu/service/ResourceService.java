package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartedu.common.PageResult;
import com.smartedu.entity.Resource;
import com.smartedu.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源服务
 * 
 * <p>
 * 处理教学资源的 CRUD 操作
 * 
 * @author SmartEducation Team
 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceMapper resourceMapper;

    /**
     * 获取所有资源列表
     */
    public List<Resource> getAllResources() {
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Resource::getCreatedAt);
        return resourceMapper.selectList(wrapper);
    }

    /**
     * 分页查询资源
     * 
     * @param page     页码
     * @param size     每页大小
     * @param category 分类筛选（可选）
     * @param keyword  关键词搜索（可选）
     */
    public PageResult<Resource> getResourcesPage(int page, int size, String category, String keyword) {
        Page<Resource> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<>();

        // 分类筛选
        if (category != null && !category.isEmpty() && !"全部".equals(category)) {
            wrapper.eq(Resource::getCategory, category);
        }

        // 关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Resource::getTitle, keyword)
                    .or()
                    .like(Resource::getSource, keyword)
                    .or()
                    .like(Resource::getContent, keyword));
        }

        wrapper.orderByDesc(Resource::getCreatedAt);

        Page<Resource> result = resourceMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据 ID 获取资源详情
     */
    public Resource getResourceById(Long id) {
        return resourceMapper.selectById(id);
    }

    /**
     * 根据来源链接获取资源
     *
     * <p>
     * 上传任务会使用合成来源链接作为幂等键，因此需要通过来源链接进行查找和更新。
     */
    public Resource getBySourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resource::getSourceUrl, sourceUrl).last("LIMIT 1");
        return resourceMapper.selectOne(wrapper);
    }

    /**
     * 按来源 URL 判重
     */
    public boolean existsBySourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resource::getSourceUrl, sourceUrl).last("LIMIT 1");
        return resourceMapper.selectCount(wrapper) > 0;
    }

    /**
     * 供 AI 对话检索上下文资源
     */
    public List<Resource> searchForChatContext(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 8));
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<>();

        List<String> searchTerms = tokenize(keyword);
        if (!searchTerms.isEmpty()) {
            wrapper.and(group -> {
                boolean firstTerm = true;
                for (String term : searchTerms) {
                    if (!firstTerm) {
                        group.or();
                    }
                    group.and(w -> w.like(Resource::getTitle, term)
                            .or()
                            .like(Resource::getContent, term)
                            .or()
                            .like(Resource::getTags, term)
                            .or()
                            .like(Resource::getIdeologySummary, term)
                            .or()
                            .like(Resource::getSource, term));
                    firstTerm = false;
                }
            });
        }

        wrapper.orderByDesc(Resource::getCreatedAt)
                .last("LIMIT " + safeLimit);

        return resourceMapper.selectList(wrapper);
    }

    /**
     * 将自然语言问题拆成短语，避免直接拿整句做 like 导致几乎无法命中数据。
     */
    private List<String> tokenize(String keyword) {
        List<String> terms = new java.util.ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return terms;
        }

        for (String part : keyword.split("[\\s,，。；;：:、|/]+")) {
            String term = part.trim();
            if (term.length() >= 2 && !terms.contains(term)) {
                terms.add(term);
            }
            if (terms.size() >= 5) {
                break;
            }
        }
        return terms;
    }

    /**
     * 创建资源
     */
    @Transactional
    public Resource createResource(Resource resource) {
        resource.setCreatedAt(LocalDateTime.now());
        resource.setUpdatedAt(LocalDateTime.now());
        if (resource.getSyncStatus() == null) {
            resource.setSyncStatus("PENDING");
        }
        resourceMapper.insert(resource);
        return resource;
    }

    /**
     * 更新资源
     */
    @Transactional
    public Resource updateResource(Resource resource) {
        resource.setUpdatedAt(LocalDateTime.now());
        resourceMapper.updateById(resource);
        return resourceMapper.selectById(resource.getId());
    }

    /**
     * 删除资源（逻辑删除）
     */
    @Transactional
    public void deleteResource(Long id) {
        resourceMapper.deleteById(id);
    }

    /**
     * 根据状态获取资源
     */
    public List<Resource> getResourcesByStatus(String status) {
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resource::getSyncStatus, status);
        wrapper.orderByDesc(Resource::getCreatedAt);
        return resourceMapper.selectList(wrapper);
    }

    /**
     * 更新资源同步状态
     */
    @Transactional
    public void updateSyncStatus(Long id, String status) {
        Resource resource = resourceMapper.selectById(id);
        if (resource != null) {
            resource.setSyncStatus(status);
            resource.setUpdatedAt(LocalDateTime.now());
            resourceMapper.updateById(resource);
        }
    }
}
