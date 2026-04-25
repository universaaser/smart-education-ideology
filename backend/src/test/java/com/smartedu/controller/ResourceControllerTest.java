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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void shouldReturnReviewResources() throws Exception {
        mockMvc.perform(get("/api/resources/review?reviewStatus=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].reviewStatus").value("PENDING"));
    }

    @Test
    void shouldUpdateReviewStatus() throws Exception {
        mockMvc.perform(put("/api/resources/7/review-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewStatus": "APPROVED",
                                  "reviewerId": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedBy").value(3));
    }

    @Test
    void shouldRejectInvalidReviewStatus() throws Exception {
        mockMvc.perform(put("/api/resources/7/review-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewStatus": "ARCHIVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid review status"));
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

        @Override
        public List<com.smartedu.entity.Resource> getReviewResources(String reviewStatus) {
            com.smartedu.entity.Resource resource = new com.smartedu.entity.Resource();
            resource.setId(7L);
            resource.setTitle("Pending Resource");
            resource.setReviewStatus("PENDING");
            return List.of(resource);
        }

        @Override
        public com.smartedu.entity.Resource updateReviewStatus(Long id, String reviewStatus, Long reviewerId) {
            com.smartedu.entity.Resource resource = new com.smartedu.entity.Resource();
            resource.setId(id);
            resource.setTitle("Reviewed Resource");
            resource.setReviewStatus(reviewStatus);
            resource.setReviewedBy(reviewerId);
            return resource;
        }
    }

    private static class StubResourceCrawlService extends ResourceCrawlService {

        StubResourceCrawlService() {
            super(List.of(), null, null, null, new ObjectMapper(), null);
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
