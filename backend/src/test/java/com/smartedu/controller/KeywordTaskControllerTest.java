package com.smartedu.controller;

import com.smartedu.dto.KeywordTaskCreateRequestDto;
import com.smartedu.dto.KeywordTaskDto;
import com.smartedu.dto.KeywordTaskItemDto;
import com.smartedu.entity.Resource;
import com.smartedu.service.KeywordTaskService;
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

class KeywordTaskControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KeywordTaskController controller = new KeywordTaskController(new StubKeywordTaskService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldCreateKeywordTask() throws Exception {
        mockMvc.perform(post("/api/keyword-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": 2,
                                  "creatorId": 5,
                                  "keywords": ["edge computing", "smart sensor"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.keywords[0]").value("edge computing"));
    }

    @Test
    void shouldRejectEmptyKeywords() throws Exception {
        mockMvc.perform(post("/api/keyword-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": 2,
                                  "keywords": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Keywords cannot be empty"));
    }

    @Test
    void shouldListKeywordTasks() throws Exception {
        mockMvc.perform(get("/api/keyword-tasks?courseId=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(11));
    }

    @Test
    void shouldReturnKeywordTaskDetail() throws Exception {
        mockMvc.perform(get("/api/keyword-tasks/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].title").value("Smart Sensor Case"));
    }

    @Test
    void shouldRunKeywordTask() throws Exception {
        mockMvc.perform(post("/api/keyword-tasks/11/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.items[0].aiSummary").value("AI summary"));
    }

    @Test
    void shouldAcceptKeywordTaskItem() throws Exception {
        mockMvc.perform(post("/api/keyword-tasks/11/items/21/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Accepted Keyword Resource"));
    }

    private static class StubKeywordTaskService extends KeywordTaskService {

        StubKeywordTaskService() {
            super(null, null, null, null);
        }

        @Override
        public List<String> normalizeKeywords(List<String> keywords) {
            if (keywords == null) {
                return List.of();
            }
            return keywords.stream().filter(value -> value != null && !value.isBlank()).toList();
        }

        @Override
        public List<KeywordTaskDto> listTasks(Long courseId) {
            return List.of(task(false, "PENDING"));
        }

        @Override
        public KeywordTaskDto getTask(Long id) {
            return task(true, "DONE");
        }

        @Override
        public KeywordTaskDto createTask(KeywordTaskCreateRequestDto request) {
            return task(false, "PENDING");
        }

        @Override
        public KeywordTaskDto runTask(Long id) {
            return task(true, "DONE");
        }

        @Override
        public Resource acceptItem(Long taskId, Long itemId) {
            Resource resource = new Resource();
            resource.setId(31L);
            resource.setTitle("Accepted Keyword Resource");
            return resource;
        }

        private KeywordTaskDto task(boolean includeItems, String status) {
            return new KeywordTaskDto(
                    11L,
                    2L,
                    5L,
                    List.of("edge computing", "smart sensor"),
                    status,
                    "Generated 1 keyword result items",
                    "",
                    LocalDateTime.of(2026, 4, 25, 3, 0),
                    LocalDateTime.of(2026, 4, 25, 2, 55),
                    LocalDateTime.of(2026, 4, 25, 3, 0),
                    includeItems ? List.of(new KeywordTaskItemDto(
                            21L,
                            11L,
                            "smart sensor",
                            "Smart Sensor Case",
                            "https://example.com/sensor",
                            "excerpt",
                            "AI summary",
                            "[]",
                            "PENDING",
                            null,
                            null,
                            null)) : List.of());
        }
    }
}
