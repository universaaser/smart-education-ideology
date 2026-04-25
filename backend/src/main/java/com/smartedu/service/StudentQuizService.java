package com.smartedu.service;

import com.smartedu.dto.StudentQuizQuestionDto;

import com.smartedu.dto.StudentQuizSubmitRequestDto;
import com.smartedu.dto.StudentQuizSubmitResultDto;
import com.smartedu.dto.TeachingArtifactsDto;
import com.smartedu.dto.TeachingMaterialViewDto;
import com.smartedu.dto.StudentEventBatchRequestDto;
import com.smartedu.dto.StudentEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentQuizService {

    private static final String SINGLE_CHOICE = "SINGLE_CHOICE";
    private static final String ANSWER_SUBMIT = "answer_submit";

    private final TeachingMaterialService teachingMaterialService;
    private final StudentActivityEventService studentActivityEventService;


    public List<StudentQuizQuestionDto> listQuestions(Long materialId) {
        TeachingMaterialViewDto material = teachingMaterialService.getMaterialById(materialId);
        List<StudentQuizQuestionDto> result = new ArrayList<>();
        List<TeachingArtifactsDto.QuestionDto> questions = material.getQuestions() == null
                ? List.of()
                : material.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            TeachingArtifactsDto.QuestionDto question = questions.get(i);
            if (!isSupportedChoiceQuestion(question)) {
                continue;
            }
            result.add(toStudentQuestion(material, question, i));
        }
        return result;
    }

    public StudentQuizSubmitResultDto submitAnswer(StudentQuizSubmitRequestDto request) {
        TeachingMaterialViewDto material = teachingMaterialService.getMaterialById(request.getMaterialId());
        List<TeachingArtifactsDto.QuestionDto> questions = material.getQuestions() == null
                ? List.of()
                : material.getQuestions();
        if (request.getQuestionIndex() == null || request.getQuestionIndex() < 0 || request.getQuestionIndex() >= questions.size()) {
            throw new IllegalArgumentException("Question index is invalid");
        }
        TeachingArtifactsDto.QuestionDto question = questions.get(request.getQuestionIndex());
        if (!isSupportedChoiceQuestion(question)) {
            throw new IllegalArgumentException("Only single choice questions are supported");
        }

        String normalizedAnswer = normalizeAnswer(request.getAnswer());
        String correctAnswer = normalizeAnswer(question.getReferenceAnswer());
        boolean correct = !normalizedAnswer.isBlank() && normalizedAnswer.equals(correctAnswer);
        String questionId = buildQuestionId(request.getMaterialId(), request.getQuestionIndex());
        Long courseId = request.getCourseId() == null ? material.getCourseId() : request.getCourseId();

        StudentEventDto event = new StudentEventDto();
        event.setEventType(ANSWER_SUBMIT);
        event.setKnowledgePointId(question.getKnowledgePointId());
        event.setOccurredAt(LocalDateTime.now());
        event.setPayload(buildPayload(request, question, questionId, correct, correctAnswer));

        StudentEventBatchRequestDto batch = new StudentEventBatchRequestDto();
        batch.setStudentId(request.getStudentId());
        batch.setCourseId(courseId);
        batch.setEvents(List.of(event));
        studentActivityEventService.saveBatch(batch);

        return new StudentQuizSubmitResultDto(questionId, correct, correctAnswer, question.getKnowledgePointId());
    }

    private StudentQuizQuestionDto toStudentQuestion(
            TeachingMaterialViewDto material,
            TeachingArtifactsDto.QuestionDto question,
            int questionIndex) {
        return new StudentQuizQuestionDto(
                buildQuestionId(material.getMaterialId(), questionIndex),
                material.getMaterialId(),
                questionIndex,
                material.getCourseId(),
                question.getKnowledgePointId(),
                normalizeType(question.getQuestionType()),
                question.getDifficulty(),
                question.getStem(),
                question.getOptions() == null ? new ArrayList<>() : question.getOptions()
        );
    }

    private boolean isSupportedChoiceQuestion(TeachingArtifactsDto.QuestionDto question) {
        return question != null
                && SINGLE_CHOICE.equals(normalizeType(question.getQuestionType()))
                && question.getOptions() != null
                && !question.getOptions().isEmpty()
                && question.getReferenceAnswer() != null
                && !question.getReferenceAnswer().isBlank();
    }

    private Map<String, Object> buildPayload(
            StudentQuizSubmitRequestDto request,
            TeachingArtifactsDto.QuestionDto question,
            String questionId,
            boolean correct,
            String correctAnswer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("materialId", request.getMaterialId());
        payload.put("questionIndex", request.getQuestionIndex());
        payload.put("questionId", questionId);
        payload.put("questionType", normalizeType(question.getQuestionType()));
        payload.put("stem", question.getStem());
        payload.put("options", question.getOptions());
        payload.put("answer", normalizeAnswer(request.getAnswer()));
        payload.put("correctAnswer", correctAnswer);
        payload.put("isCorrect", correct);
        payload.put("knowledgePointId", question.getKnowledgePointId());
        return payload;
    }

    private String normalizeType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "";
        }
        return questionType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.trim().toUpperCase(Locale.ROOT);
    }

    private String buildQuestionId(Long materialId, int questionIndex) {
        return materialId + "#" + questionIndex;
    }
}
