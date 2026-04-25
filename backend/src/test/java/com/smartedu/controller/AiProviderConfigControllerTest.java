package com.smartedu.controller;

import com.smartedu.dto.AiProviderConfigDto;
import com.smartedu.dto.AiProviderConfigRequestDto;
import com.smartedu.dto.AiProviderTestResultDto;
import com.smartedu.dto.AiRouteConfigDto;
import com.smartedu.dto.AiRouteConfigRequestDto;
import com.smartedu.service.AiProviderConfigService;
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

class AiProviderConfigControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiProviderConfigController controller = new AiProviderConfigController(new StubAiProviderConfigService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldListProvidersWithoutApiKeyValue() throws Exception {
        mockMvc.perform(get("/api/admin/ai-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].providerKey").value("openai"))
                .andExpect(jsonPath("$.data[0].keyConfigured").value(true));
    }

    @Test
    void shouldSaveProvider() throws Exception {
        mockMvc.perform(put("/api/admin/ai-providers/openai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "OpenAI Compatible",
                                  "enabled": true,
                                  "apiBase": "http://localhost:8317/v1",
                                  "model": "gpt-5.4",
                                  "apiKey": "secret",
                                  "timeoutSeconds": 120
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.providerKey").value("openai"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.keyConfigured").value(true));
    }

    @Test
    void shouldRejectUnsupportedProvider() throws Exception {
        mockMvc.perform(put("/api/admin/ai-providers/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported AI provider"));
    }

    @Test
    void shouldTestProvider() throws Exception {
        mockMvc.perform(post("/api/admin/ai-providers/openai/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.providerKey").value("openai"))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void shouldListRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/ai-providers/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].taskType").value("chat"))
                .andExpect(jsonPath("$.data[0].providerKey").value("openai"));
    }

    @Test
    void shouldSaveRoute() throws Exception {
        mockMvc.perform(put("/api/admin/ai-providers/routes/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerKey": "openai",
                                  "model": "gpt-5.4"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskType").value("chat"))
                .andExpect(jsonPath("$.data.model").value("gpt-5.4"));
    }

    @Test
    void shouldRejectUnsupportedRouteTask() throws Exception {
        mockMvc.perform(put("/api/admin/ai-providers/routes/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported AI route task"));
    }

    private static class StubAiProviderConfigService extends AiProviderConfigService {

        StubAiProviderConfigService() {
            super(null, null);
        }

        @Override
        public List<AiProviderConfigDto> listProviders() {
            return List.of(provider());
        }

        @Override
        public AiProviderConfigDto saveProvider(String providerKey, AiProviderConfigRequestDto request) {
            if (!supportsProvider(providerKey) || request == null) {
                return null;
            }
            return provider();
        }

        @Override
        public AiProviderTestResultDto testProvider(String providerKey) {
            return new AiProviderTestResultDto(providerKey, true, "Provider configuration is complete");
        }

        @Override
        public List<AiRouteConfigDto> listRoutes() {
            return List.of(route());
        }

        @Override
        public AiRouteConfigDto saveRoute(String taskType, AiRouteConfigRequestDto request) {
            if (!supportsTask(taskType) || request == null) {
                return null;
            }
            return new AiRouteConfigDto(2L, taskType, request.getProviderKey(), request.getModel(), now(), now());
        }

        @Override
        public boolean supportsProvider(String providerKey) {
            return "openai".equals(providerKey) || "deepseek".equals(providerKey) || "proxy".equals(providerKey) || "gemini".equals(providerKey);
        }

        @Override
        public boolean supportsTask(String taskType) {
            return "chat".equals(taskType) || "parse".equals(taskType) || "question-gen".equals(taskType);
        }

        private AiProviderConfigDto provider() {
            return new AiProviderConfigDto(
                    1L,
                    "openai",
                    "OpenAI Compatible",
                    true,
                    "http://localhost:8317/v1",
                    "gpt-5.4",
                    true,
                    120,
                    now(),
                    now());
        }

        private AiRouteConfigDto route() {
            return new AiRouteConfigDto(2L, "chat", "openai", "gpt-5.4", now(), now());
        }

        private LocalDateTime now() {
            return LocalDateTime.of(2026, 4, 25, 3, 0);
        }
    }
}
