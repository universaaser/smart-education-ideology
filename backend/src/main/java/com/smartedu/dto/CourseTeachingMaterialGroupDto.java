package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Grouped teaching material summary for one course.
 *
 * <p>
 * Each group represents one upload/parse task and keeps its saved versions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseTeachingMaterialGroupDto {

    private Long parseTaskId;

    private Long courseId;

    private Long chapterId;

    private String displayTitle;

    private String sourceFileName;

    private Long latestMaterialId;

    private Integer latestVersionNo;

    private String latestStatus;

    private LocalDateTime updatedAt;

    private List<MaterialVersionItemDto> versions = new ArrayList<>();
}
