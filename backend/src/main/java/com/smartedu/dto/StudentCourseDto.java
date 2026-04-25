package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentCourseDto {
    private Long id;
    private String name;
    private Integer progress;
    private String ideologyScore;
    private String gradeColor;
    private String gradeLabel;
    private String code;
    private String description;
    private String semester;
}
