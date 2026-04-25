package com.smartedu.dto;

import lombok.Data;

@Data
public class CourseChapterRequestDto {

    private Long parentId;

    private String title;

    private Integer sortOrder;
}
