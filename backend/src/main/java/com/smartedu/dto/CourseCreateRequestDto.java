package com.smartedu.dto;

import lombok.Data;

/**
 * Course create request payload.
 */
@Data
public class CourseCreateRequestDto {

    private String name;
    private String code;
    private String description;
    private String semester;
    private Long teacherId;
}
