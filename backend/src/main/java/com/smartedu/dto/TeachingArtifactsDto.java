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
         * 题型。
         */
        private String questionType;

        /**
         * 难度。
         */
        private String difficulty;

        /**
         * 关联知识点 ID。
         */
        private Long knowledgePointId;

        /**
         * 题干。
         */
        private String stem;

        /**
         * 选项。
         */
        private List<String> options = new ArrayList<>();

        /**
         * 参考答案。
         */
        private String referenceAnswer;

        /**
         * 评分要点。
         */
        private List<String> scoringPoints = new ArrayList<>();

        public QuestionDto(String stem, String referenceAnswer, List<String> scoringPoints) {
            this.questionType = "SHORT_ANSWER";
            this.difficulty = "MEDIUM";
            this.stem = stem;
            this.options = new ArrayList<>();
            this.referenceAnswer = referenceAnswer;
            this.scoringPoints = scoringPoints == null ? new ArrayList<>() : scoringPoints;
        }
    }
}
