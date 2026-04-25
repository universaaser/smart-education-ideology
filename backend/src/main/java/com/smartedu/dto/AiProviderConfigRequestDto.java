package com.smartedu.dto;

import lombok.Data;

@Data
public class AiProviderConfigRequestDto {
    private String label;
    private Boolean enabled;
    private String apiBase;
    private String model;
    private String apiKey;
    private Integer timeoutSeconds;
}
