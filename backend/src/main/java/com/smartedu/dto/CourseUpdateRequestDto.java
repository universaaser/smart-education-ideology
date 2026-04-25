package com.smartedu.dto;

import lombok.Data;

@Data
public class CourseUpdateRequestDto {

    private String name;
    private String code;
    private String description;
    private Long teacherId;
    private String semester;
    private Integer progress;
    private String ideologyScore;
    private String coverImage;
    private Integer status;
}
