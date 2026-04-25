package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for saving teaching material content.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingMaterialSaveRequestDto {

    private Long chapterId;

    private String title;

    private String lectureNotes;

    private List<String> cases = new ArrayList<>();

    private List<TeachingArtifactsDto.QuestionDto> questions = new ArrayList<>();
}
