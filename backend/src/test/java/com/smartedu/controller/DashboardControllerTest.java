package com.smartedu.controller;

import com.smartedu.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DashboardController controller = new DashboardController(new StubDashboardService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnDashboardOverview() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.todoCards[0].title").value("Open Alerts"))
                .andExpect(jsonPath("$.data.todoCards[0].count").value(2))
                .andExpect(jsonPath("$.data.recentParseTasks[0].fileName").value("chapter.pdf"))
                .andExpect(jsonPath("$.data.materialSummary.publishedCount").value(3))
                .andExpect(jsonPath("$.data.activityTrend[0].value").value(5));
    }

    private static class StubDashboardService extends DashboardService {

        StubDashboardService() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public Map<String, Object> getOverview(Long teacherId) {
            return Map.of(
                    "todoCards", List.of(Map.of(
                            "key", "alerts",
                            "title", "Open Alerts",
                            "description", "Students need teacher attention",
                            "count", 2,
                            "view", "ALERTS",
                            "status", "warning")),
                    "recentParseTasks", List.of(Map.of(
                            "id", 7,
                            "fileName", "chapter.pdf",
                            "status", "COMPLETED",
                            "progress", 100,
                            "updatedAt", "2026-04-25T17:00")),
                    "materialSummary", Map.of(
                            "draftCount", 1,
                            "publishedCount", 3,
                            "latestCount", 4),
                    "activityTrend", List.of(Map.of(
                            "day", "2026-04-25",
                            "value", 5)));
        }
    }
}
