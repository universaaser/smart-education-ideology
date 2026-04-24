package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.AuthBootstrapResponse;
import com.smartedu.dto.AuthLoginRequestDto;
import com.smartedu.dto.AuthRegisterRequestDto;
import com.smartedu.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Authentication controller.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Handle login with a structured request payload.
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody(required = false) AuthLoginRequestDto request) {
        if (request == null) {
            return Result.badRequest("Request body cannot be empty");
        }
        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.trim().isEmpty()) {
            return Result.badRequest("Username cannot be empty");
        }
        if (password == null || password.isEmpty()) {
            return Result.badRequest("Password cannot be empty");
        }

        try {
            Map<String, Object> result = authService.login(username.trim(), password);
            return Result.success("Login successful", result);
        } catch (RuntimeException e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * Handle register with a structured request payload.
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody(required = false) AuthRegisterRequestDto request) {
        if (request == null) {
            return Result.badRequest("Request body cannot be empty");
        }
        String username = request.getUsername();
        String password = request.getPassword();
        String email = request.getEmail();
        String role = request.getRole();

        if (username == null || username.trim().isEmpty()) {
            return Result.badRequest("Username cannot be empty");
        }
        if (password == null || password.length() < 6) {
            return Result.badRequest("Password must be at least 6 characters");
        }

        try {
            Map<String, Object> result = authService.register(username.trim(), password, email, role);
            return Result.success("Register successful", result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get current user info.
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.unauthorized("Invalid authorization header");
        }

        String token = authorization.substring(7);
        try {
            Map<String, Object> userInfo = authService.getUserByToken(token);
            return Result.success(userInfo);
        } catch (RuntimeException e) {
            return Result.unauthorized(e.getMessage());
        }
    }

    /**
     * Provide bootstrap data for the frontend shell.
     */
    @GetMapping("/bootstrap")
    public Result<AuthBootstrapResponse> getBootstrap(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.unauthorized("Invalid authorization header");
        }

        String token = authorization.substring(7);
        try {
            AuthBootstrapResponse result = authService.getBootstrapByToken(token);
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.unauthorized(e.getMessage());
        }
    }
}
