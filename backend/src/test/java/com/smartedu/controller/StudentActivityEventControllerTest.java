package com.smartedu.controller;

import com.smartedu.dto.StudentLearningReportDto;
import com.smartedu.dto.StudentRecentActivityDto;
import com.smartedu.service.StudentActivityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentActivityEventControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StudentActivityEventController controller = new StudentActivityEventController(new StubStudentActivityEventService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldAcceptBatchEvents() throws Exception {
        mockMvc.perform(post("/api/student/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": 9,
                                  "courseId": 3,
                                  "events": [{"eventType":"knowledge_view","knowledgePointId":11}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.savedCount").value(1));
    }

    @Test
    void shouldRejectEmptyEvents() throws Exception {
        mockMvc.perform(post("/api/student/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": 9,
                                  "courseId": 3,
                                  "events": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Events cannot be empty"));
    }

    @Test
    void shouldReturnLearningReport() throws Exception {
        mockMvc.perform(get("/api/student/report?studentId=9&courseId=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.todayStudyMinutes").value(12))
                .andExpect(jsonPath("$.data.totalStudyMinutes").value(48))
                .andExpect(jsonPath("$.data.todayEventCount").value(4))
                .andExpect(jsonPath("$.data.knowledgeViewCount").value(2));
    }

    @Test
    void shouldReturnRecentActivities() throws Exception {
        mockMvc.perform(get("/api/student/recent-activities?studentId=9&courseId=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].eventType").value("ai_ask"));
    }

    private static class StubStudentActivityEventService extends StudentActivityEventService {

        StubStudentActivityEventService() {
            super(null, null, null);
        }

        @Override
        public int saveBatch(com.smartedu.dto.StudentEventBatchRequestDto request) {
            return request.getEvents().size();
        }

        @Override
        public StudentLearningReportDto getReport(Long studentId, Long courseId) {
            return new StudentLearningReportDto(
                    12,
                    48,
                    4,
                    2,
                    3,
                    2,
                    66.7,
                    List.of(11L),
                    List.of(new StudentLearningReportDto.TrendItem("2026-04-24", 4))
            );
        }

        @Override
        public List<StudentRecentActivityDto> getRecentActivities(Long studentId, Long courseId, int limit) {
            return List.of(new StudentRecentActivityDto(1L, courseId, "ai_ask", "AI question submitted", "AI question submitted", LocalDateTime.now()));
        }
    }
}
