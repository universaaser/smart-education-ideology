package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentRecentActivityDto {

    private Long id;
    private Long courseId;
    private String eventType;
    private String title;
    private String description;
    private LocalDateTime occurredAt;
}
