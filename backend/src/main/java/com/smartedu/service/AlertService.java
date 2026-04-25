package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.StudentAlertRecordDto;
import com.smartedu.dto.StudentAlertSummaryDto;
import com.smartedu.entity.StudentActivity;
import com.smartedu.entity.StudentActivityEvent;
import com.smartedu.entity.StudentAlertRecord;
import com.smartedu.mapper.StudentActivityEventMapper;
import com.smartedu.mapper.StudentActivityMapper;
import com.smartedu.mapper.StudentAlertRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预警服务
 *
 * <p>
 * 实现学生自适应学习监测和预警逻辑
 * <p>
 * 基于三维评估指标：学习时长、答题正确率、专注度
 *
 * @author SmartEducation Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final StudentActivityMapper studentActivityMapper;
    private final StudentActivityEventMapper studentActivityEventMapper;
    private final StudentAlertRecordMapper studentAlertRecordMapper;
    private final ObjectMapper objectMapper;

    // 预警阈值常量
    private static final double FOCUS_WARNING_THRESHOLD = 60.0; // 专注度低于60%触发警告
    private static final double CORRECT_RATE_WARNING_THRESHOLD = 50.0; // 正确率低于50%触发警告
    private static final int STUDY_DURATION_MIN_THRESHOLD = 10; // 最短学习时长（分钟）
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "PROCESSING");
    private static final Set<String> SUPPORTED_STATUSES = Set.of("PENDING", "PROCESSING", "RESOLVED", "IGNORED");

    // 情绪权重配置
    private static final Map<String, Double> EMOTION_WEIGHTS;
    static {
        Map<String, Double> map = new HashMap<>();
        map.put("NORMAL", 0.0);
        map.put("CONFUSED", 0.3);
        map.put("ANGRY", 0.5);
        map.put("DISTRACTED", 0.4);
        EMOTION_WEIGHTS = Collections.unmodifiableMap(map);
    }

    /**
     * 计算预警等级
     *
     * <p>
     * 基于三维评估指标和情绪状态计算综合预警等级
     *
     * @param focusScore    专注度评分（0-100）
     * @param correctRate   答题正确率（0-100）
     * @param studyDuration 学习时长（分钟）
     * @param emotionStatus 情绪状态
     * @return 预警等级：0-正常 1-轻度 2-中度 3-重度
     */
    public int calculateAlertLevel(
            BigDecimal focusScore,
            BigDecimal correctRate,
            Integer studyDuration,
            String emotionStatus) {

        double alertScore = 0.0;

        // 1. 专注度指标（权重 0.4）
        if (focusScore != null) {
            double focus = focusScore.doubleValue();
            if (focus < FOCUS_WARNING_THRESHOLD) {
                // 专注度越低，分数越高
                alertScore += (1 - focus / 100) * 0.4;
            }
        }

        // 2. 正确率指标（权重 0.3）
        if (correctRate != null) {
            double rate = correctRate.doubleValue();
            if (rate < CORRECT_RATE_WARNING_THRESHOLD) {
                alertScore += (1 - rate / 100) * 0.3;
            }
        }

        // 3. 学习时长指标（权重 0.1）
        if (studyDuration != null && studyDuration < STUDY_DURATION_MIN_THRESHOLD) {
            alertScore += 0.1;
        }

        // 4. 情绪状态指标（权重 0.2）
        if (emotionStatus != null) {
            alertScore += EMOTION_WEIGHTS.getOrDefault(emotionStatus, 0.0) * 0.2;
        }

        // 根据综合分数确定预警等级
        if (alertScore >= 0.7) {
            return 3; // 重度预警
        } else if (alertScore >= 0.4) {
            return 2; // 中度预警
        } else if (alertScore >= 0.2) {
            return 1; // 轻度预警
        } else {
            return 0; // 正常
        }
    }

    /**
     * 生成预警消息
     */
    public String generateAlertMessage(int alertLevel, String emotionStatus) {
        switch (alertLevel) {
            case 3:
                return "【重度预警】学生学习状态异常，建议立即关注并进行一对一辅导";
            case 2:
                return "【中度预警】学生专注度下降明显，建议调整教学节奏或互动方式";
            case 1:
                return "【轻度预警】学生可能存在理解困难，建议适当放慢进度";
            default:
                return null;
        }
    }

    /**
     * 分析学生学习行为并更新预警状态
     *
     * @param activity 学生活动记录
     * @return 更新后的活动记录
     */
    public StudentActivity analyzeAndUpdate(StudentActivity activity) {
        // 计算预警等级
        int alertLevel = calculateAlertLevel(
                activity.getFocusScore(),
                activity.getCorrectRate(),
                activity.getStudyDuration(),
                activity.getEmotionStatus());

        activity.setAlertLevel(alertLevel);

        // 生成预警消息
        String alertMessage = generateAlertMessage(alertLevel, activity.getEmotionStatus());
        activity.setAlertMessage(alertMessage);

        // 更新数据库
        studentActivityMapper.updateById(activity);

        if (alertLevel > 0) {
            log.warn("学生预警触发: studentId={}, level={}, message={}",
                    activity.getStudentId(), alertLevel, alertMessage);
        }

        return activity;
    }

    public List<StudentAlertRecordDto> evaluateStudentAlerts(Long studentId, Long courseId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(6).atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        List<StudentActivityEvent> events = studentActivityEventMapper.selectList(new LambdaQueryWrapper<StudentActivityEvent>()
                .eq(StudentActivityEvent::getStudentId, studentId)
                .eq(StudentActivityEvent::getCourseId, courseId)
                .ge(StudentActivityEvent::getOccurredAt, start)
                .le(StudentActivityEvent::getOccurredAt, end));

        long eventCount = events.size();
        long studySeconds = events.stream()
                .filter(event -> event.getDurationSeconds() != null)
                .mapToLong(StudentActivityEvent::getDurationSeconds)
                .sum();
        long materialOpenCount = countByType(events, "material_open");
        long knowledgeViewCount = countByType(events, "knowledge_view");
        long aiAskCount = countByType(events, "ai_ask");
        List<StudentActivityEvent> quizEvents = events.stream()
                .filter(event -> "answer_submit".equals(event.getEventType()))
                .toList();
        long quizAnswerCount = quizEvents.size();
        long correctQuizAnswerCount = quizEvents.stream()
                .filter(this::isCorrectQuizAnswer)
                .count();
        double quizCorrectRate = quizAnswerCount == 0 ? 0D : correctQuizAnswerCount * 100D / quizAnswerCount;

        List<StudentAlertRecord> records = new ArrayList<>();
        String evidence = evidenceOf(eventCount, studySeconds, materialOpenCount, knowledgeViewCount, aiAskCount);
        if (eventCount == 0) {
            records.add(buildRecord(studentId, courseId, "LOW_ACTIVITY", 3,
                    "No learning activity detected",
                    "No student learning activity was captured in the last 7 days.",
                    "Start with one course resource and ask the AI assistant for help after reading.",
                    evidence));
        } else {
            if (studySeconds < 600) {
                records.add(buildRecord(studentId, courseId, "LOW_STUDY_TIME", 2,
                        "Low study time",
                        "The student has less than 10 minutes of captured study time in the last 7 days.",
                        "Spend at least 10 minutes reviewing the current course materials today.",
                        evidence));
            }
            if (aiAskCount >= 3 && knowledgeViewCount == 0) {
                records.add(buildRecord(studentId, courseId, "CONFUSION_RISK", 2,
                        "Possible learning confusion",
                        "The student asked multiple AI questions without opening knowledge points.",
                        "Open the knowledge graph first, then continue asking focused AI questions.",
                        evidence));
            }
            if (materialOpenCount == 0) {
                records.add(buildRecord(studentId, courseId, "LOW_RESOURCE_ENGAGEMENT", 1,
                        "No material engagement",
                        "The student has activity records but no course material opening event.",
                        "Open one course material and record key points before the next activity.",
                        evidence));
            }
            if (quizAnswerCount >= 3 && quizCorrectRate < CORRECT_RATE_WARNING_THRESHOLD) {
                records.add(buildRecord(studentId, courseId, "LOW_QUIZ_ACCURACY", 2,
                        "Low quiz accuracy",
                        "The student's quiz accuracy is below 50% in recent answer records.",
                        "Review the weak knowledge points and retry the related quiz questions.",
                        evidence));
            }
        }

        List<StudentAlertRecord> savedRecords = new ArrayList<>();
        for (StudentAlertRecord record : records) {
            if (!hasActiveAlert(record.getStudentId(), record.getCourseId(), record.getAlertType())) {
                studentAlertRecordMapper.insert(record);
                savedRecords.add(record);
            }
        }
        return savedRecords.stream().map(this::toDto).toList();
    }

    private boolean isCorrectQuizAnswer(StudentActivityEvent event) {
        if (event.getPayloadJson() == null || event.getPayloadJson().isBlank()) {
            return false;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(event.getPayloadJson(), new TypeReference<>() {});
            return Boolean.TRUE.equals(payload.get("isCorrect"));
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    public StudentAlertSummaryDto getAlertSummary(Long courseId) {
        List<StudentAlertRecord> records = studentAlertRecordMapper.selectList(alertQuery(courseId, null, null));
        return new StudentAlertSummaryDto(
                records.size(),
                countByStatus(records, "PENDING"),
                countByStatus(records, "PROCESSING"),
                countByStatus(records, "RESOLVED"),
                countByStatus(records, "IGNORED"),
                countByLevel(records, 1),
                countByLevel(records, 2),
                countByLevel(records, 3)
        );
    }

    public List<StudentAlertRecordDto> listAlerts(Long courseId, Integer alertLevel, String status) {
        return studentAlertRecordMapper.selectList(alertQuery(courseId, alertLevel, status)
                        .orderByDesc(StudentAlertRecord::getGeneratedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public StudentAlertRecordDto updateAlertStatus(Long id, String status) {
        StudentAlertRecord record = studentAlertRecordMapper.selectById(id);
        if (record == null || !SUPPORTED_STATUSES.contains(status)) {
            return null;
        }
        record.setStatus(status);
        record.setHandledAt(ACTIVE_STATUSES.contains(status) ? null : LocalDateTime.now());
        studentAlertRecordMapper.updateById(record);
        return toDto(record);
    }

    public List<StudentAlertRecordDto> getStudentFeedback(Long studentId, Long courseId) {
        LambdaQueryWrapper<StudentAlertRecord> wrapper = new LambdaQueryWrapper<StudentAlertRecord>()
                .eq(StudentAlertRecord::getStudentId, studentId)
                .in(StudentAlertRecord::getStatus, ACTIVE_STATUSES)
                .orderByDesc(StudentAlertRecord::getAlertLevel)
                .orderByDesc(StudentAlertRecord::getGeneratedAt)
                .last("LIMIT 5");
        if (courseId != null) {
            wrapper.eq(StudentAlertRecord::getCourseId, courseId);
        }
        return studentAlertRecordMapper.selectList(wrapper).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 获取预警统计
     */
    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 统计各预警等级的数量（简化实现）
        stats.put("total", studentActivityMapper.selectCount(null));
        stats.put("normal", 0);
        stats.put("mild", 0);
        stats.put("moderate", 0);
        stats.put("severe", 0);

        return stats;
    }

    private LambdaQueryWrapper<StudentAlertRecord> alertQuery(Long courseId, Integer alertLevel, String status) {
        LambdaQueryWrapper<StudentAlertRecord> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(StudentAlertRecord::getCourseId, courseId);
        }
        if (alertLevel != null) {
            wrapper.eq(StudentAlertRecord::getAlertLevel, alertLevel);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(StudentAlertRecord::getStatus, status);
        }
        return wrapper;
    }

    private boolean hasActiveAlert(Long studentId, Long courseId, String alertType) {
        return studentAlertRecordMapper.selectCount(new LambdaQueryWrapper<StudentAlertRecord>()
                .eq(StudentAlertRecord::getStudentId, studentId)
                .eq(StudentAlertRecord::getCourseId, courseId)
                .eq(StudentAlertRecord::getAlertType, alertType)
                .in(StudentAlertRecord::getStatus, ACTIVE_STATUSES)) > 0;
    }

    private StudentAlertRecord buildRecord(Long studentId, Long courseId, String type, int level, String title, String message, String suggestion, String evidence) {
        StudentAlertRecord record = new StudentAlertRecord();
        record.setStudentId(studentId);
        record.setCourseId(courseId);
        record.setAlertType(type);
        record.setAlertLevel(level);
        record.setTitle(title);
        record.setMessage(message);
        record.setSuggestion(suggestion);
        record.setStatus("PENDING");
        record.setEvidenceJson(evidence);
        record.setGeneratedAt(LocalDateTime.now());
        return record;
    }

    private StudentAlertRecordDto toDto(StudentAlertRecord record) {
        return new StudentAlertRecordDto(
                record.getId(),
                record.getStudentId(),
                record.getCourseId(),
                record.getAlertType(),
                record.getAlertLevel(),
                record.getTitle(),
                record.getMessage(),
                record.getSuggestion(),
                record.getStatus(),
                record.getGeneratedAt(),
                record.getHandledAt()
        );
    }

    private long countByType(List<StudentActivityEvent> events, String eventType) {
        return events.stream().filter(event -> eventType.equals(event.getEventType())).count();
    }

    private long countByStatus(List<StudentAlertRecord> records, String status) {
        return records.stream().filter(record -> status.equals(record.getStatus())).count();
    }

    private long countByLevel(List<StudentAlertRecord> records, int alertLevel) {
        return records.stream().filter(record -> record.getAlertLevel() != null && record.getAlertLevel() == alertLevel).count();
    }

    private String evidenceOf(long eventCount, long studySeconds, long materialOpenCount, long knowledgeViewCount, long aiAskCount) {
        return String.format(
                "{\"eventCount\":%d,\"studySeconds\":%d,\"materialOpenCount\":%d,\"knowledgeViewCount\":%d,\"aiAskCount\":%d}",
                eventCount,
                studySeconds,
                materialOpenCount,
                knowledgeViewCount,
                aiAskCount);
    }
}
