package com.smartedu.dto;

import lombok.Data;

@Data
public class StudentQuizSubmitRequestDto {

    private Long studentId;
    private Long courseId;
    private Long materialId;
    private Integer questionIndex;
    private String answer;
}
