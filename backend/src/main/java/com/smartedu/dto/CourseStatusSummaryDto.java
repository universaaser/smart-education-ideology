package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseStatusSummaryDto {

    private long chapterCount;

    private long parseTaskCount;

    private long materialCount;

    private long draftMaterialCount;

    private long publishedMaterialCount;

    private long knowledgePointCount;
}
