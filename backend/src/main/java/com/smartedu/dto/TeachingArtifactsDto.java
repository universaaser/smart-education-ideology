package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 教学内容生成结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingArtifactsDto {

    /**
     * 讲义主体内容。
     */
    private String lectureNotes;

    /**
     * 案例列表。
     */
    private List<String> cases = new ArrayList<>();

    /**
     * 题目列表。
     */
    private List<QuestionDto> questions = new ArrayList<>();

    /**
     * 题目结构。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDto {

        /**
         * 题干。
         */
        private String stem;

        /**
         * 参考答案。
         */
        private String referenceAnswer;

        /**
         * 评分要点。
         */
        private List<String> scoringPoints = new ArrayList<>();
    }
}
