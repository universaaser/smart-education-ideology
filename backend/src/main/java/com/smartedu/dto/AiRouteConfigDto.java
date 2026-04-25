package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRouteConfigDto {
    private Long id;
    private String taskType;
    private String providerKey;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
