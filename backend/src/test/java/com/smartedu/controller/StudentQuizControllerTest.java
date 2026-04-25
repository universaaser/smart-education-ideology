package com.smartedu.controller;

import com.smartedu.dto.StudentQuizQuestionDto;
import com.smartedu.dto.StudentQuizSubmitRequestDto;
import com.smartedu.dto.StudentQuizSubmitResultDto;
import com.smartedu.service.StudentQuizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentQuizControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StudentQuizController controller = new StudentQuizController(new StubStudentQuizService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnQuizQuestionsWithoutAnswer() throws Exception {
        mockMvc.perform(get("/api/student/quiz/questions?materialId=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].questionId").value("7#0"))
                .andExpect(jsonPath("$.data[0].options[0]").value("A. Option A"));
    }

    @Test
    void shouldRejectEmptyMaterialId() throws Exception {
        mockMvc.perform(get("/api/student/quiz/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Material id cannot be empty"));
    }

    @Test
    void shouldSubmitQuizAnswer() throws Exception {
        mockMvc.perform(post("/api/student/quiz/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": 9,
                                  "courseId": 3,
                                  "materialId": 7,
                                  "questionIndex": 0,
                                  "answer": "A"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.correct").value(true));
    }

    @Test
    void shouldRejectEmptyAnswer() throws Exception {
        mockMvc.perform(post("/api/student/quiz/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": 9,
                                  "courseId": 3,
                                  "materialId": 7,
                                  "questionIndex": 0,
                                  "answer": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Answer cannot be empty"));
    }

    private static class StubStudentQuizService extends StudentQuizService {

        StubStudentQuizService() {
            super(null, null);
        }

        @Override
        public List<StudentQuizQuestionDto> listQuestions(Long materialId) {
            return List.of(new StudentQuizQuestionDto(
                    materialId + "#0",
                    materialId,
                    0,
                    3L,
                    11L,
                    "SINGLE_CHOICE",
                    "EASY",
                    "Choose one option",
                    List.of("A. Option A", "B. Option B")
            ));
        }

        @Override
        public StudentQuizSubmitResultDto submitAnswer(StudentQuizSubmitRequestDto request) {
            return new StudentQuizSubmitResultDto(
                    request.getMaterialId() + "#" + request.getQuestionIndex(),
                    "A".equalsIgnoreCase(request.getAnswer()),
                    "A",
                    11L
            );
        }
    }
}
