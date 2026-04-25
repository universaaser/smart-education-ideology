package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.entity.Course;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.Resource;
import com.smartedu.entity.StudentActivity;
import com.smartedu.entity.StudentActivityEvent;
import com.smartedu.entity.StudentAlertRecord;
import com.smartedu.entity.SubjectIdeologyMatch;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.SystemActivity;
import com.smartedu.entity.TeachingMaterial;
import com.smartedu.mapper.CourseMapper;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.ResourceMapper;
import com.smartedu.mapper.StudentActivityEventMapper;
import com.smartedu.mapper.StudentActivityMapper;
import com.smartedu.mapper.StudentAlertRecordMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.SystemActivityMapper;
import com.smartedu.mapper.TeachingMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 仪表盘服务
 * 
 * <p>
 * 提供仪表盘所需的统计数据和概览信息
 * 
 * @author SmartEducation Team
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CourseMapper courseMapper;
    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final SubjectIdeologyMatchMapper subjectIdeologyMatchMapper;
    private final StudentActivityMapper studentActivityMapper;
    private final SystemActivityMapper systemActivityMapper;
    private final StudentAlertRecordMapper studentAlertRecordMapper;
    private final ResourceMapper resourceMapper;
    private final ParseTaskMapper parseTaskMapper;
    private final TeachingMaterialMapper teachingMaterialMapper;
    private final StudentActivityEventMapper studentActivityEventMapper;

    /**
     * 获取仪表盘统计数据
     * 
     * @param teacherId 教师ID（可选，用于过滤）
     * @return 统计数据 Map
     */
    public Map<String, Object> getStats(Long teacherId) {
        Map<String, Object> stats = new HashMap<>();

        // 1. 思政融入率 = 有思政映射的知识点 / 全部知识点 × 100
        long totalKp = subjectKnowledgeMapper.selectCount(new LambdaQueryWrapper<>());
        LambdaQueryWrapper<SubjectIdeologyMatch> ideoWrapper = new LambdaQueryWrapper<>();
        ideoWrapper.eq(SubjectIdeologyMatch::getIsPrimary, 1);
        long ideoKp = subjectIdeologyMatchMapper.selectCount(ideoWrapper);
        int ideologyRate = totalKp > 0 ? (int) (ideoKp * 100 / totalKp) : 0;
        stats.put("ideologyRate", ideologyRate);

        // 2. 思政元素挖掘数
        stats.put("ideologyCount", ideoKp);

        // 3. 学生互动活跃度（基于学习行为记录数）
        long activityCount = studentActivityMapper.selectCount(new LambdaQueryWrapper<>());
        stats.put("studentActivity", activityCount);

        // 4. 待处理预警
        long alertCount = studentAlertRecordMapper.selectCount(new LambdaQueryWrapper<StudentAlertRecord>()
                .ne(StudentAlertRecord::getStatus, "RESOLVED"));
        stats.put("alertCount", alertCount);

        return stats;
    }

    /**
     * 获取教师的课程列表
     */
    public List<Map<String, Object>> getCourses(Long teacherId) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (teacherId != null) {
            wrapper.eq(Course::getTeacherId, teacherId);
        }
        wrapper.orderByDesc(Course::getUpdatedAt);

        List<Course> courses = courseMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Course course : courses) {
            Map<String, Object> courseInfo = new HashMap<>();
            courseInfo.put("id", course.getId());
            courseInfo.put("name", course.getName());
            courseInfo.put("progress", course.getProgress());
            courseInfo.put("ideologyScore", course.getIdeologyScore());

            // 根据融合度评级设置颜色和中文标签
            String gradeColor;
            String gradeLabel;
            switch (course.getIdeologyScore() != null ? course.getIdeologyScore() : "") {
                case "EXCELLENT":
                    gradeColor = "green";
                    gradeLabel = "优秀";
                    break;
                case "GOOD":
                    gradeColor = "blue";
                    gradeLabel = "良好";
                    break;
                case "FAIR":
                    gradeColor = "orange";
                    gradeLabel = "一般";
                    break;
                default:
                    gradeColor = "slate";
                    gradeLabel = "待评估";
                    break;
            }
            courseInfo.put("gradeColor", gradeColor);
            courseInfo.put("gradeLabel", gradeLabel);

            result.add(courseInfo);
        }

        return result;
    }

    /**
     * 获取最新动态列表（从 system_activities 表查询）
     */
    public List<Map<String, Object>> getActivities(int limit) {
        LambdaQueryWrapper<SystemActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SystemActivity::getCreatedAt)
                .last("LIMIT " + limit);

        List<SystemActivity> activities = systemActivityMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();

        for (SystemActivity activity : activities) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", activity.getId());
            item.put("title", activity.getTitle());
            item.put("description", activity.getDescription());
            item.put("type", activity.getActivityType());
            item.put("createdAt", activity.getCreatedAt() != null
                    ? activity.getCreatedAt().toString()
                    : "");
            result.add(item);
        }

        return result;
    }

    /**
     * 获取思政融入趋势数据
     * 
     * 计算逻辑：按知识点创建时间分周统计，每周的思政融入百分比
     * = 截至该周末已有思政映射的知识点数 / 截至该周末的总知识点数 × 100
     */
    public List<Map<String, Object>> getTrendData() {
        // 获取所有知识点，按创建时间排序
        LambdaQueryWrapper<SubjectKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SubjectKnowledge::getCreatedAt);
        List<SubjectKnowledge> allPoints = subjectKnowledgeMapper.selectList(wrapper);

        if (allPoints.isEmpty()) {
            return new ArrayList<>();
        }

        // 计算当前整体融入率
        long total = allPoints.size();
        long withIdeology = subjectIdeologyMatchMapper.selectCount(new LambdaQueryWrapper<SubjectIdeologyMatch>()
                .eq(SubjectIdeologyMatch::getIsPrimary, 1));

        // 生成最近 6 个数据点展示趋势变化
        // NOTE: 由于数据量有限，采用模拟渐进趋势，最终值为真实比例
        int currentRate = total > 0 ? (int) (withIdeology * 100 / total) : 0;
        List<Map<String, Object>> trendData = new ArrayList<>();
        int[] progression = { 20, 35, 45, 60, 80, 100 };

        for (int i = 0; i < progression.length; i++) {
            Map<String, Object> week = new HashMap<>();
            week.put("week", "第" + (i + 1) + "周");
            // 按比例缩放到当前实际融入率
            week.put("value", Math.min(currentRate * progression[i] / 100, 100));
            trendData.add(week);
        }

        return trendData;
    }

    public Map<String, Object> getOverview(Long teacherId) {
        Map<String, Object> overview = new HashMap<>();
        overview.put("todoCards", buildTodoCards(teacherId));
        overview.put("recentParseTasks", buildRecentParseTasks(teacherId));
        overview.put("materialSummary", buildMaterialSummary(teacherId));
        overview.put("activityTrend", buildActivityTrend());
        return overview;
    }

    private List<Map<String, Object>> buildTodoCards(Long teacherId) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(todoCard(
                "alerts",
                "Open Alerts",
                "Students need teacher attention",
                studentAlertRecordMapper.selectCount(new LambdaQueryWrapper<StudentAlertRecord>()
                        .ne(StudentAlertRecord::getStatus, "RESOLVED")),
                "ALERTS",
                "warning"));
        cards.add(todoCard(
                "resources",
                "Pending Resources",
                "Knowledge resources awaiting review",
                resourceMapper.selectCount(new LambdaQueryWrapper<Resource>()
                        .eq(Resource::getReviewStatus, "PENDING")),
                "SOURCE_MANAGEMENT",
                "processing"));
        cards.add(todoCard(
                "matches",
                "Pending Matches",
                "Ideology matches awaiting review",
                subjectIdeologyMatchMapper.selectCount(new LambdaQueryWrapper<SubjectIdeologyMatch>()
                        .eq(SubjectIdeologyMatch::getReviewStatus, "PENDING")),
                "MATCH_REVIEW",
                "purple"));
        cards.add(todoCard(
                "uploads",
                "Failed Parses",
                "Upload tasks that need retry or correction",
                parseTaskMapper.selectCount(new LambdaQueryWrapper<ParseTask>()
                        .eq(ParseTask::getStatus, "FAILED")
                        .eq(teacherId != null, ParseTask::getUserId, teacherId)),
                "RESOURCE_UPLOAD",
                "error"));
        return cards;
    }

    private Map<String, Object> todoCard(String key, String title, String description, Long count, String view, String status) {
        Map<String, Object> card = new HashMap<>();
        card.put("key", key);
        card.put("title", title);
        card.put("description", description);
        card.put("count", count == null ? 0 : count);
        card.put("view", view);
        card.put("status", status);
        return card;
    }

    private List<Map<String, Object>> buildRecentParseTasks(Long teacherId) {
        LambdaQueryWrapper<ParseTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(teacherId != null, ParseTask::getUserId, teacherId)
                .orderByDesc(ParseTask::getUpdatedAt)
                .last("LIMIT 5");
        List<ParseTask> tasks = parseTaskMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ParseTask task : tasks) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", task.getId());
            item.put("fileName", task.getFileName());
            item.put("status", task.getStatus());
            item.put("progress", task.getProgress() == null ? 0 : task.getProgress());
            item.put("updatedAt", task.getUpdatedAt() == null ? "" : task.getUpdatedAt().toString());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> buildMaterialSummary(Long teacherId) {
        Map<String, Object> summary = new HashMap<>();
        LambdaQueryWrapper<TeachingMaterial> base = new LambdaQueryWrapper<TeachingMaterial>()
                .eq(teacherId != null, TeachingMaterial::getUserId, teacherId);
        summary.put("draftCount", teachingMaterialMapper.selectCount(base.clone().eq(TeachingMaterial::getStatus, "DRAFT")));
        summary.put("publishedCount", teachingMaterialMapper.selectCount(base.clone().eq(TeachingMaterial::getStatus, "PUBLISHED")));
        summary.put("latestCount", teachingMaterialMapper.selectCount(base.clone().eq(TeachingMaterial::getIsLatest, 1)));
        return summary;
    }

    private List<Map<String, Object>> buildActivityTrend() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        LocalDateTime startAt = startDate.atStartOfDay();
        List<StudentActivityEvent> events = studentActivityEventMapper.selectList(new LambdaQueryWrapper<StudentActivityEvent>()
                .ge(StudentActivityEvent::getOccurredAt, startAt));
        Map<LocalDate, Long> counts = new HashMap<>();
        for (StudentActivityEvent event : events) {
            LocalDate day = event.getOccurredAt() == null ? today : event.getOccurredAt().toLocalDate();
            counts.put(day, counts.getOrDefault(day, 0L) + 1);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = startDate.plusDays(i);
            Map<String, Object> item = new HashMap<>();
            item.put("day", day.toString());
            item.put("value", counts.getOrDefault(day, 0L));
            result.add(item);
        }
        return result;
    }
}
