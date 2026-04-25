package com.smartedu.controller;

import com.smartedu.dto.StudentAlertRecordDto;
import com.smartedu.dto.StudentAlertSummaryDto;
import com.smartedu.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AlertController controller = new AlertController(new StubAlertService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldEvaluateStudentAlerts() throws Exception {
        mockMvc.perform(post("/api/alerts/evaluate?studentId=9&courseId=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].alertType").value("LOW_STUDY_TIME"));
    }

    @Test
    void shouldRejectEvaluateWithoutStudentId() throws Exception {
        mockMvc.perform(post("/api/alerts/evaluate?courseId=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Student id cannot be empty"));
    }

    @Test
    void shouldReturnAlertSummary() throws Exception {
        mockMvc.perform(get("/api/alerts/summary?courseId=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pending").value(2))
                .andExpect(jsonPath("$.data.severe").value(1));
    }

    @Test
    void shouldListAlerts() throws Exception {
        mockMvc.perform(get("/api/alerts?courseId=3&alertLevel=2&status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void shouldRejectInvalidAlertFilter() throws Exception {
        mockMvc.perform(get("/api/alerts?alertLevel=5&status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid alert level"));
    }

    @Test
    void shouldUpdateAlertStatus() throws Exception {
        mockMvc.perform(put("/api/alerts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"resolved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    void shouldRejectInvalidAlertStatusUpdate() throws Exception {
        mockMvc.perform(put("/api/alerts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CLOSED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid alert status"));
    }

    @Test
    void shouldReturnStudentFeedback() throws Exception {
        mockMvc.perform(get("/api/student/feedback?studentId=9&courseId=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].suggestion").value("Review one material."));
    }

    private static class StubAlertService extends AlertService {

        StubAlertService() {
            super(null, null, null, null);
        }

        @Override
        public List<StudentAlertRecordDto> evaluateStudentAlerts(Long studentId, Long courseId) {
            return List.of(alert("LOW_STUDY_TIME", "PENDING"));
        }

        @Override
        public StudentAlertSummaryDto getAlertSummary(Long courseId) {
            return new StudentAlertSummaryDto(4, 2, 1, 1, 0, 1, 2, 1);
        }

        @Override
        public List<StudentAlertRecordDto> listAlerts(Long courseId, Integer alertLevel, String status) {
            return List.of(alert("LOW_STUDY_TIME", status));
        }

        @Override
        public StudentAlertRecordDto updateAlertStatus(Long id, String status) {
            return alert("LOW_STUDY_TIME", status);
        }

        @Override
        public List<StudentAlertRecordDto> getStudentFeedback(Long studentId, Long courseId) {
            return List.of(alert("LOW_RESOURCE_ENGAGEMENT", "PENDING"));
        }

        private StudentAlertRecordDto alert(String type, String status) {
            return new StudentAlertRecordDto(
                    1L,
                    9L,
                    3L,
                    type,
                    2,
                    "Low study time",
                    "The student has low study time.",
                    "Review one material.",
                    status,
                    LocalDateTime.now(),
                    null
            );
        }
    }
}
