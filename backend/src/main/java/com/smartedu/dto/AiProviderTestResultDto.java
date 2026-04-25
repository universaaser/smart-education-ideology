package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderTestResultDto {
    private String providerKey;
    private Boolean success;
    private String message;
}
