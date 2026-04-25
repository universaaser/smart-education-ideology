package com.smartedu.dto;

import lombok.Data;

import java.util.List;

@Data
public class KeywordTaskCreateRequestDto {

    private Long courseId;

    private Long creatorId;

    private List<String> keywords;
}
