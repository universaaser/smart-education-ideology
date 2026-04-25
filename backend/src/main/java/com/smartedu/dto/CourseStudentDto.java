package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseStudentDto {
    private Long courseId;
    private Long studentId;
    private String username;
    private String realName;
    private String email;
    private String department;
    private LocalDateTime boundAt;
}
