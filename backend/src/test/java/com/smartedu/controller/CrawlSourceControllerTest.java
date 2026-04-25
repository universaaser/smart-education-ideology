package com.smartedu.controller;

import com.smartedu.crawler.model.CrawlTaskStatus;
import com.smartedu.dto.CrawlSourceRequestDto;
import com.smartedu.entity.CrawlRunLog;
import com.smartedu.entity.CrawlSource;
import com.smartedu.service.CrawlSourceService;
import com.smartedu.service.ResourceCrawlService;
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

class CrawlSourceControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CrawlSourceController controller = new CrawlSourceController(new StubCrawlSourceService(), new StubResourceCrawlService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnCrawlSources() throws Exception {
        mockMvc.perform(get("/api/crawl-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("People Daily"))
                .andExpect(jsonPath("$.data[0].enabled").value(1));
    }

    @Test
    void shouldCreateCrawlSource() throws Exception {
        mockMvc.perform(post("/api/crawl-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Xinhua",
                                  "baseUrl": "https://example.com/news",
                                  "enabled": true,
                                  "remark": "manual source"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Xinhua"));
    }

    @Test
    void shouldUpdateCrawlSource() throws Exception {
        mockMvc.perform(put("/api/crawl-sources/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "People Daily Updated",
                                  "baseUrl": "https://people.example.com",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(0));
    }

    @Test
    void shouldTriggerSourceCrawl() throws Exception {
        mockMvc.perform(post("/api/crawl-sources/5/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.state").value("RUNNING"));
    }

    @Test
    void shouldReturnRunLogs() throws Exception {
        mockMvc.perform(get("/api/crawl-sources/runs?sourceId=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value("DONE"));
    }

    private static class StubCrawlSourceService extends CrawlSourceService {

        StubCrawlSourceService() {
            super(null, null, null);
        }

        @Override
        public List<CrawlSource> listSources() {
            CrawlSource source = new CrawlSource();
            source.setId(5L);
            source.setName("People Daily");
            source.setBaseUrl("https://people.example.com");
            source.setEnabled(1);
            return List.of(source);
        }

        @Override
        public CrawlSource createSource(CrawlSourceRequestDto request) {
            CrawlSource source = new CrawlSource();
            source.setId(6L);
            source.setName(request.getName());
            source.setBaseUrl(request.getBaseUrl());
            source.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
            source.setRemark(request.getRemark());
            return source;
        }

        @Override
        public CrawlSource updateSource(Long id, CrawlSourceRequestDto request) {
            CrawlSource source = createSource(request);
            source.setId(id);
            return source;
        }

        @Override
        public List<CrawlRunLog> listRunLogs(Long sourceId) {
            CrawlRunLog log = new CrawlRunLog();
            log.setId(9L);
            log.setSourceId(sourceId);
            log.setStatus("DONE");
            log.setTotalCreated(2);
            return List.of(log);
        }
    }

    private static class StubResourceCrawlService extends ResourceCrawlService {

        StubResourceCrawlService() {
            super(List.of(), null, null, null, null, null);
        }

        @Override
        public CrawlTaskStatus startManualCrawl(Long sourceId) {
            return new CrawlTaskStatus(
                    "RUNNING",
                    false,
                    LocalDateTime.of(2026, 4, 25, 2, 0),
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
