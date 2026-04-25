package com.smartedu.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class StudentEventDto {

    private String eventType;
    private Long knowledgePointId;
    private Integer durationSeconds;
    private Map<String, Object> payload;
    private LocalDateTime occurredAt;
}
