package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.AuthBootstrapResponse;
import com.smartedu.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * 
 * <p>
 * 处理用户登录、注册等认证相关接口
 * 
 * @author SmartEducation Team
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     * 
     * @param request 包含 username 和 password 的请求体
     * @return 包含 token 和用户信息的响应
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        // 参数校验
        if (username == null || username.isEmpty()) {
            return Result.badRequest("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return Result.badRequest("密码不能为空");
        }

        try {
            Map<String, Object> result = authService.login(username, password);
            return Result.success("登录成功", result);
        } catch (RuntimeException e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 用户注册
     * 
     * @param request 包含 username、password、email、role 的请求体
     * @return 包含 token 和用户信息的响应
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String email = request.get("email");
        String role = request.get("role");

        // 参数校验
        if (username == null || username.isEmpty()) {
            return Result.badRequest("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            return Result.badRequest("密码长度至少6位");
        }

        try {
            Map<String, Object> result = authService.register(username, password, email, role);
            return Result.success("注册成功", result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     * 
     * @param authorization Authorization 请求头
     * @return 用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.unauthorized("未提供有效的令牌");
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
     * Provide user info together with role-based UI contract for future frontend split.
     */
    @GetMapping("/bootstrap")
    public Result<AuthBootstrapResponse> getBootstrap(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.unauthorized("未提供有效的令牌");
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
