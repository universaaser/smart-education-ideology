package com.smartedu.controller;

import com.smartedu.common.PageResult;
import com.smartedu.common.Result;
import com.smartedu.crawler.model.CrawlTaskStatus;
import com.smartedu.dto.ResourceManualCrawlRequestDto;
import com.smartedu.entity.Resource;
import com.smartedu.service.ResourceCrawlService;
import com.smartedu.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资源控制器
 */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final ResourceCrawlService resourceCrawlService;

    /** 获取所有资源 */
    @GetMapping
    public Result<List<Resource>> getAllResources() {
        List<Resource> resources = resourceService.getAllResources();
        return Result.success(resources);
    }

    /** 分页查询资源 */
    @GetMapping("/page")
    public Result<PageResult<Resource>> getResourcesPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        PageResult<Resource> result = resourceService.getResourcesPage(page, size, category, keyword);
        return Result.success(result);
    }

    /** 获取资源详情 */
    @GetMapping("/{id}")
    public Result<Resource> getResourceById(@PathVariable Long id) {
        Resource resource = resourceService.getResourceById(id);
        if (resource == null) {
            return Result.notFound("资源不存在");
        }
        return Result.success(resource);
    }

    /** 创建资源 */
    @PostMapping
    public Result<Resource> createResource(@RequestBody Resource resource) {
        if (resource.getTitle() == null || resource.getTitle().isEmpty()) {
            return Result.badRequest("资源标题不能为空");
        }
        Resource created = resourceService.createResource(resource);
        return Result.success("资源创建成功", created);
    }

    /**
     * 启动手动抓取任务
     */
    @PostMapping("/crawl/start")
    public Result<CrawlTaskStatus> startCrawlUpdate() {
        CrawlTaskStatus status = resourceCrawlService.startManualCrawl();
        String message = "RUNNING".equals(status.getState()) ? "数据库更新任务已启动" : "数据库更新任务已在运行";
        return Result.success(message, status);
    }

    /**
     * 请求停止抓取任务（当前文章处理完成后停止）
     */
    @PostMapping("/crawl/stop")
    public Result<CrawlTaskStatus> stopCrawlUpdate() {
        CrawlTaskStatus status = resourceCrawlService.requestStop();
        return Result.success("已提交停止请求", status);
    }

    /**
     * 获取抓取任务状态
     */
    @GetMapping("/crawl/status")
    public Result<CrawlTaskStatus> getCrawlStatus() {
        CrawlTaskStatus status = resourceCrawlService.getTaskStatus();
        return Result.success(status);
    }

    /**
     * 兼容旧接口：内部转发到 start
     */
    @PostMapping("/crawl/update")
    public Result<CrawlTaskStatus> manualCrawlUpdate(
            @RequestBody(required = false) ResourceManualCrawlRequestDto request) {
        // 保留 request 参数以兼容历史调用；当前版本固定每站新增目标=2
        CrawlTaskStatus status = resourceCrawlService.startManualCrawl();
        String message = "RUNNING".equals(status.getState()) ? "数据库更新任务已启动" : "数据库更新任务已在运行";
        return Result.success(message, status);
    }

    /** 更新资源 */
    @PutMapping("/{id}")
    public Result<Resource> updateResource(
            @PathVariable Long id,
            @RequestBody Resource resource) {
        resource.setId(id);
        Resource updated = resourceService.updateResource(resource);
        return Result.success("资源更新成功", updated);
    }

    /** 删除资源 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteResource(@PathVariable Long id) {
        resourceService.deleteResource(id);
        return Result.success("资源已删除", null);
    }

    /** 根据同步状态查询资源 */
    @GetMapping("/status/{status}")
    public Result<List<Resource>> getResourcesByStatus(@PathVariable String status) {
        List<Resource> resources = resourceService.getResourcesByStatus(status);
        return Result.success(resources);
    }
}
