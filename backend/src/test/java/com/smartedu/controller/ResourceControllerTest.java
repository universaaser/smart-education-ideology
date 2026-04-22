package com.smartedu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.crawler.model.CrawlTaskStatus;
import com.smartedu.service.ResourceCrawlService;
import com.smartedu.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResourceController controller = new ResourceController(new StubResourceService(), new StubResourceCrawlService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldAcceptLegacyManualCrawlPayloadWithoutUsingMap() throws Exception {
        mockMvc.perform(post("/api/resources/crawl/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetCreatedPerSite": 5,
                                  "legacyFlag": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.state").value("RUNNING"))
                .andExpect(jsonPath("$.data.targetCreatedPerSite").value(2));
    }

    private static class StubResourceService extends ResourceService {

        StubResourceService() {
            super(null, null);
        }

        @Override
        public List<com.smartedu.entity.Resource> getAllResources() {
            return List.of();
        }
    }

    private static class StubResourceCrawlService extends ResourceCrawlService {

        StubResourceCrawlService() {
            super(List.of(), null, null, null, new ObjectMapper());
        }

        @Override
        public CrawlTaskStatus startManualCrawl() {
            return new CrawlTaskStatus(
                    "RUNNING",
                    false,
                    LocalDateTime.of(2026, 4, 21, 20, 0),
                    null,
                    "",
                    "",
                    2,
                    60,
                    null,
                    null,
                    "task started");
        }
    }
}
