package com.smartedu.service;

import com.smartedu.entity.StudentActivity;
import com.smartedu.mapper.StudentActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

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

    // 预警阈值常量
    private static final double FOCUS_WARNING_THRESHOLD = 60.0; // 专注度低于60%触发警告
    private static final double CORRECT_RATE_WARNING_THRESHOLD = 50.0; // 正确率低于50%触发警告
    private static final int STUDY_DURATION_MIN_THRESHOLD = 10; // 最短学习时长（分钟）

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
}
