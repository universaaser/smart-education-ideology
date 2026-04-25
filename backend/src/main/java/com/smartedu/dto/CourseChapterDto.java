package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseChapterDto {

    private Long id;

    private Long courseId;

    private Long parentId;

    private String title;

    private Integer sortOrder;

    private LocalDateTime updatedAt;
}
