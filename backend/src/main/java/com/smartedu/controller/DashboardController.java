package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘控制器
 * 
 * <p>
 * 提供仪表盘页面所需的各类统计数据和概览信息
 * 
 * @author SmartEducation Team
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取仪表盘统计数据
     * 
     * @param teacherId 教师ID（可选）
     * @return 统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(required = false) Long teacherId) {
        Map<String, Object> stats = dashboardService.getStats(teacherId);
        return Result.success(stats);
    }

    /**
     * 获取课程列表
     * 
     * @param teacherId 教师ID（可选）
     * @return 课程列表
     */
    @GetMapping("/courses")
    public Result<List<Map<String, Object>>> getCourses(
            @RequestParam(required = false) Long teacherId) {
        List<Map<String, Object>> courses = dashboardService.getCourses(teacherId);
        return Result.success(courses);
    }

    /**
     * 获取最新动态
     * 
     * @param limit 返回条数限制，默认10
     * @return 动态列表
     */
    @GetMapping("/activities")
    public Result<List<Map<String, Object>>> getActivities(
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> activities = dashboardService.getActivities(limit);
        return Result.success(activities);
    }

    /**
     * 获取思政融入趋势数据
     * 
     * @return 趋势数据
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrendData() {
        List<Map<String, Object>> trendData = dashboardService.getTrendData();
        return Result.success(trendData);
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam(required = false) Long teacherId) {
        return Result.success(dashboardService.getOverview(teacherId));
    }
}
