package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.constant.UserRoleConstants;
import com.smartedu.dto.AuthBootstrapResponse;
import com.smartedu.entity.User;
import com.smartedu.mapper.UserMapper;
import com.smartedu.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import cn.hutool.crypto.digest.BCrypt;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户认证服务
 * 
 * <p>
 * 处理用户登录、注册等认证相关业务逻辑
 * 
 * @author SmartEducation Team
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RoleAccessService roleAccessService;

    /**
     * 用户登录
     * 
     * @param username 用户名
     * @param password 密码
     * @return 包含 token 和用户信息的 Map
     * @throws RuntimeException 登录失败时抛出异常
     */
    public Map<String, Object> login(String username, String password) {
        // 查询用户
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证密码（使用 BCrypt）
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 检查用户状态
        if (user.getStatus() != 1) {
            throw new RuntimeException("账户已被禁用");
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 生成 JWT 令牌
        String normalizedRole = UserRoleConstants.normalize(user.getRole());
        user.setRole(normalizedRole);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), normalizedRole);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", buildUserInfo(user));

        return result;
    }

    /**
     * 用户注册
     * 
     * @param username 用户名
     * @param password 密码
     * @param email    邮箱
     * @param role     角色
     * @return 注册成功的用户信息
     */
    public Map<String, Object> register(String username, String password, String email, String role) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 创建新用户
        User user = new User();
        user.setUsername(username);
        // 使用 BCrypt 加密密码
        user.setPassword(BCrypt.hashpw(password));
        user.setEmail(email);
        // 角色处理：统一转换为大写，并校验合法性
        String userRole = UserRoleConstants.normalize(role);
        user.setRole(userRole);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);

        // 生成令牌
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", buildUserInfo(user));

        return result;
    }

    /**
     * 根据令牌获取用户信息
     */
    public Map<String, Object> getUserByToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("令牌无效");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        User user = userMapper.selectById(userId);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return buildUserInfo(user);
    }

    /**
     * Build a role-aware bootstrap response for future student and teacher shells.
     */
    public AuthBootstrapResponse getBootstrapByToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("令牌无效");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        User user = userMapper.selectById(userId);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return new AuthBootstrapResponse(
                buildUserInfo(user),
                roleAccessService.buildRoleUiConfig(user.getRole()));
    }

    /**
     * 构建用户信息（不包含敏感字段）
     */
    private Map<String, Object> buildUserInfo(User user) {
        String normalizedRole = UserRoleConstants.normalize(user.getRole());
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("realName", user.getRealName());
        userInfo.put("role", normalizedRole);
        userInfo.put("roleLabel", UserRoleConstants.getRoleLabel(normalizedRole));
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("department", user.getDepartment());
        return userInfo;
    }
}
