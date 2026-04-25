package com.smartedu.dto;

import lombok.Data;

import java.util.List;

@Data
public class StudentEventBatchRequestDto {

    private Long studentId;
    private Long courseId;
    private List<StudentEventDto> events;
}
