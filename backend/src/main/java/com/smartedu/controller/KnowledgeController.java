package com.smartedu.controller;

import com.smartedu.common.PageResult;
import com.smartedu.common.Result;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.dto.NodePositionUpdateRequestDto;
import com.smartedu.entity.KnowledgeRelation;
import com.smartedu.service.KnowledgeExcelService;
import com.smartedu.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.Map;

/**
 * 知识图谱控制器
 * 
 * <p>
 * 处理知识点节点和关系的 CRUD 操作
 * 
 * @author SmartEducation Team
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeExcelService knowledgeExcelService;


    /**
     * 获取完整图谱数据（节点+关系）
     * 
     * @return 包含 nodes 和 connections 的图谱数据
     */
    @GetMapping("/graph")
    public Result<Map<String, Object>> getGraphData() {
        Map<String, Object> graphData = knowledgeService.getGraphData();
        return Result.success(graphData);
    }

    /**
     * 获取所有节点
     */
    @GetMapping("/nodes")
    public Result<List<KnowledgeNodeView>> getAllNodes() {
        List<KnowledgeNodeView> nodes = knowledgeService.getAllNodes();
        return Result.success(nodes);
    }

    /**
     * 分页查询节点
     */
    @GetMapping("/nodes/page")
    public Result<PageResult<KnowledgeNodeView>> getNodesPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        PageResult<KnowledgeNodeView> result = knowledgeService.getNodesPage(page, size, category);
        return Result.success(result);
    }

    /**
     * 搜索节点
     */
    @GetMapping("/nodes/search")
    public Result<List<KnowledgeNodeView>> searchNodes(@RequestParam String keyword) {
        List<KnowledgeNodeView> nodes = knowledgeService.searchNodes(keyword);
        return Result.success(nodes);
    }

    /**
     * 获取节点详情
     */
    @GetMapping("/nodes/{id}")
    public Result<KnowledgeNodeView> getNodeById(@PathVariable Long id) {
        KnowledgeNodeView node = knowledgeService.getNodeById(id);
        if (node == null) {
            return Result.notFound("节点不存在");
        }
        return Result.success(node);
    }

    /**
     * 创建节点
     */
    @PostMapping("/nodes")
    public Result<KnowledgeNodeView> createNode(@RequestBody KnowledgeNodeView node) {
        if (node.getName() == null || node.getName().isEmpty()) {
            return Result.badRequest("节点名称不能为空");
        }
        KnowledgeNodeView created = knowledgeService.createNode(node);
        return Result.success("节点创建成功", created);
    }

    /**
     * 更新节点
     */
    @PutMapping("/nodes/{id}")
    public Result<KnowledgeNodeView> updateNode(
            @PathVariable Long id,
            @RequestBody KnowledgeNodeView node) {
        node.setId(id);
        KnowledgeNodeView updated = knowledgeService.updateNode(node);
        return Result.success("节点更新成功", updated);
    }

    /**
     * 更新节点位置
     */
    @PatchMapping("/nodes/{id}/position")
    public Result<Void> updateNodePosition(
            @PathVariable Long id,
            @RequestBody NodePositionUpdateRequestDto request) {
        Double x = request.getX();
        Double y = request.getY();
        knowledgeService.updateNodePosition(id, x, y);
        return Result.success("位置已更新", null);
    }

    /**
     * 删除节点
     */
    @DeleteMapping("/nodes/{id}")
    public Result<Void> deleteNode(@PathVariable Long id) {
        knowledgeService.deleteNode(id);
        return Result.success("节点已删除", null);
    }

    /**
     * 获取所有关系
     */
    @GetMapping("/relations")
    public Result<List<KnowledgeRelation>> getAllConnections() {
        List<KnowledgeRelation> relations = knowledgeService.getAllConnections();
        return Result.success(relations);
    }

    /**
     * 创建关系
     */
    @PostMapping("/relations")
    public Result<KnowledgeRelation> createRelation(@RequestBody KnowledgeRelation relation) {
        try {
            KnowledgeRelation created = knowledgeService.createRelation(relation);
            return Result.success("Relation created successfully", created);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PutMapping("/relations/{id}")
    public Result<KnowledgeRelation> updateRelation(@PathVariable Long id, @RequestBody KnowledgeRelation relation) {
        try {
            KnowledgeRelation updated = knowledgeService.updateRelation(id, relation);
            return Result.success("Relation updated successfully", updated);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    /**
     * 删除关系
     */
    @DeleteMapping("/relations/{id}")
    public Result<Void> deleteRelation(@PathVariable Long id) {
        try {
            knowledgeService.deleteRelation(id);
            return Result.success("Relation deleted successfully", null);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PostMapping("/relations/undo-latest")
    public Result<KnowledgeRelation> undoLatestRelationChange() {
        try {
            KnowledgeRelation relation = knowledgeService.undoLatestRelationChange();
            return Result.success("Relation change undone successfully", relation);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    /**
     * 下载 Excel 导入模板
     *
     * <p>
     * 返回模板文件字节流，前端触发浏览器下载
     */
    @GetMapping("/excel/template")
    public ResponseEntity<byte[]> downloadExcelTemplate() {
        byte[] templateBytes = knowledgeExcelService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        // NOTE: 文件名使用 RFC 5987 编码避免中文乱码
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                java.net.URLEncoder.encode("知识图谱导入模板.xlsx", java.nio.charset.StandardCharsets.UTF_8));
        return ResponseEntity.ok().headers(headers).body(templateBytes);
    }

    /**
     * 从 Excel 文件导入知识图谱
     *
     * <p>
     * 解析 Excel 中的节点和关系数据，批量写入数据库
     *
     * @param file 上传的 Excel 文件（.xlsx）
     * @return 导入结果统计
     */
    @PostMapping("/excel/import")
    public Result<KnowledgeExcelService.ImportResult> importFromExcel(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.badRequest("上传的文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            return Result.badRequest("仅允许上传 .xlsx 或 .xls 格式文件");
        }
        try {
            KnowledgeExcelService.ImportResult result = knowledgeExcelService.importFromExcel(file.getInputStream());
            return Result.success("导入完成：共创建 " + result.createdNodeCount + " 个节点", result);
        } catch (Exception e) {
            return Result.error("导入失败: " + e.getMessage());
        }
    }
}
