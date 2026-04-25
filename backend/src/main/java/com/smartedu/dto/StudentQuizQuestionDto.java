package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentQuizQuestionDto {

    private String questionId;
    private Long materialId;
    private Integer questionIndex;
    private Long courseId;
    private Long knowledgePointId;
    private String questionType;
    private String difficulty;
    private String stem;
    private List<String> options = new ArrayList<>();
}
