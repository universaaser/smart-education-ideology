package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentQuizSubmitResultDto {

    private String questionId;
    private boolean correct;
    private String correctAnswer;
    private Long knowledgePointId;
}
