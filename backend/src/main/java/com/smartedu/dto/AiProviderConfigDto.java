package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderConfigDto {
    private Long id;
    private String providerKey;
    private String label;
    private Boolean enabled;
    private String apiBase;
    private String model;
    private Boolean keyConfigured;
    private Integer timeoutSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
