package com.smartedu.dto;

import lombok.Data;

@Data
public class CrawlSourceRequestDto {

    private String name;

    private String baseUrl;

    private Boolean enabled;

    private String remark;
}
