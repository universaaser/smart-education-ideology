package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentLearningReportDto {

    private int todayStudyMinutes;
    private int totalStudyMinutes;
    private long todayEventCount;
    private long knowledgeViewCount;
    private long quizAnswerCount;
    private long correctQuizAnswerCount;
    private double quizCorrectRate;
    private List<Long> weakKnowledgePointIds;
    private List<TrendItem> weeklyTrend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendItem {
        private String date;
        private long eventCount;
    }
}
