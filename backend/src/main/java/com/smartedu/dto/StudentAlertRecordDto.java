package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAlertRecordDto {

    private Long id;
    private Long studentId;
    private Long courseId;
    private String alertType;
    private Integer alertLevel;
    private String title;
    private String message;
    private String suggestion;
    private String status;
    private LocalDateTime generatedAt;
    private LocalDateTime handledAt;
}
