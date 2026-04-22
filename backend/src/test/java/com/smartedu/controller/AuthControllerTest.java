package com.smartedu.controller;

import com.smartedu.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(new StubAuthService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldLoginFromStructuredRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "teacher",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("token-123"))
                .andExpect(jsonPath("$.data.user.username").value("teacher"));
    }

    @Test
    void shouldRejectLoginWithoutUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Username cannot be empty"));
    }

    @Test
    void shouldRegisterFromStructuredRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student",
                                  "password": "secret123",
                                  "email": "student@example.com",
                                  "role": "student"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.user.username").value("student"));
    }

    private static class StubAuthService extends AuthService {

        StubAuthService() {
            super(null, null, null);
        }

        @Override
        public Map<String, Object> login(String username, String password) {
            return Map.of(
                    "token", "token-123",
                    "user", Map.of("username", username));
        }

        @Override
        public Map<String, Object> register(String username, String password, String email, String role) {
            return Map.of(
                    "token", "token-456",
                    "user", Map.of("username", username));
        }
    }
}
