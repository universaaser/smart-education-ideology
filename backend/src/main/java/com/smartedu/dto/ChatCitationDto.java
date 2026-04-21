package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatCitationDto {

    private String itemType;

    private Long referenceId;

    private String title;

    private String snippet;

    private String source;

    private String sourceUrl;

    private String matchedBy;

    private Double score;
}
