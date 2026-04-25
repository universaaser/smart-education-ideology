package com.smartedu.controller;

import com.smartedu.dto.MatchReviewDto;
import com.smartedu.dto.MatchReviewHistoryDto;
import com.smartedu.dto.MatchReviewUpdateRequestDto;
import com.smartedu.service.MatchReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchReviewControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MatchReviewController controller = new MatchReviewController(new StubMatchReviewService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldListPendingMatches() throws Exception {
        mockMvc.perform(get("/api/matches/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data[0].subjectKnowledgeName").value("Edge Computing"));
    }

    @Test
    void shouldRejectInvalidReviewStatus() throws Exception {
        mockMvc.perform(get("/api/matches/pending?status=ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid review status"));
    }

    @Test
    void shouldApproveMatch() throws Exception {
        mockMvc.perform(put("/api/matches/9/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 5,
                                  "reviewComment": "Looks good"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void shouldReviseMatchReason() throws Exception {
        mockMvc.perform(post("/api/matches/9/revise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 5,
                                  "matchReason": "Updated reason"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.matchReason").value("Updated reason"));
    }

    @Test
    void shouldRejectEmptyReviseReason() throws Exception {
        mockMvc.perform(post("/api/matches/9/revise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 5,
                                  "matchReason": " "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Match reason cannot be empty"));
    }

    @Test
    void shouldReturnNotFoundWhenRevisingMissingMatch() throws Exception {
        mockMvc.perform(post("/api/matches/404/revise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId": 5,
                                  "matchReason": "Updated reason"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Match not found"));
    }

    @Test
    void shouldReturnHistory() throws Exception {
        mockMvc.perform(get("/api/matches/9/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].action").value("APPROVE"));
    }

    private static class StubMatchReviewService extends MatchReviewService {

        StubMatchReviewService() {
            super(null, null, null, null);
        }

        @Override
        public List<MatchReviewDto> listMatches(String status) {
            return List.of(match("PENDING", "Original reason"));
        }

        @Override
        public MatchReviewDto approve(Long id, MatchReviewUpdateRequestDto request) {
            return match("APPROVED", "Original reason");
        }

        @Override
        public MatchReviewDto revise(Long id, MatchReviewUpdateRequestDto request) {
            if (id == 404L) {
                return null;
            }
            return match("PENDING", request.getMatchReason());
        }

        @Override
        public List<MatchReviewHistoryDto> listHistory(Long matchId) {
            return List.of(new MatchReviewHistoryDto(
                    3L,
                    matchId,
                    "APPROVE",
                    "PENDING",
                    "APPROVED",
                    "Original reason",
                    "Original reason",
                    5L,
                    "Looks good",
                    2,
                    LocalDateTime.of(2026, 4, 25, 2, 25)));
        }

        private MatchReviewDto match(String status, String reason) {
            return new MatchReviewDto(
                    9L,
                    2L,
                    "Edge Computing",
                    "Computer Science",
                    "IoT",
                    4L,
                    "Innovation",
                    "Innovation value",
                    1,
                    BigDecimal.valueOf(0.92),
                    reason,
                    status,
                    "APPROVED".equals(status) ? 2 : 1,
                    5L,
                    LocalDateTime.of(2026, 4, 25, 2, 20),
                    "Looks good",
                    LocalDateTime.of(2026, 4, 25, 2, 10));
        }
    }
}
