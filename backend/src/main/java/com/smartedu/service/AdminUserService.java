package com.smartedu.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartedu.constant.UserRoleConstants;
import com.smartedu.dto.AdminUserDto;
import com.smartedu.dto.AdminUserRequestDto;
import com.smartedu.dto.PageResultDto;
import com.smartedu.entity.User;
import com.smartedu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;

    public PageResultDto<AdminUserDto> listUsers(String keyword, String role, int page, int size) {
        int nextPage = Math.max(page, 1);
        int nextSize = Math.max(1, Math.min(size, 100));
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        String nextKeyword = keyword == null ? "" : keyword.trim();
        if (!nextKeyword.isEmpty()) {
            wrapper.and(query -> query.like(User::getUsername, nextKeyword)
                    .or()
                    .like(User::getRealName, nextKeyword)
                    .or()
                    .like(User::getEmail, nextKeyword));
        }
        String nextRole = role == null ? "" : role.trim().toUpperCase();
        if (UserRoleConstants.TEACHER.equals(nextRole)
                || UserRoleConstants.STUDENT.equals(nextRole)
                || UserRoleConstants.ADMIN.equals(nextRole)) {
            wrapper.eq(User::getRole, nextRole);
        }
        wrapper.orderByDesc(User::getCreatedAt).orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(nextPage, nextSize), wrapper);
        return new PageResultDto<>(
                result.getRecords().stream().map(this::toDto).toList(),
                result.getTotal(),
                nextPage,
                nextSize);
    }

    @Transactional
    public AdminUserDto createUser(AdminUserRequestDto request) {
        User user = new User();
        user.setUsername(trim(request.getUsername(), 50));
        user.setPassword(BCrypt.hashpw(request.getPassword()));
        user.setEmail(trim(request.getEmail(), 100));
        user.setRealName(trim(request.getRealName(), 50));
        user.setRole(UserRoleConstants.normalize(request.getRole()));
        user.setDepartment(trim(request.getDepartment(), 100));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return toDto(user);
    }

    @Transactional
    public AdminUserDto updateUser(Long id, AdminUserRequestDto request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return null;
        }
        if (request.getEmail() != null) {
            user.setEmail(trim(request.getEmail(), 100));
        }
        if (request.getRealName() != null) {
            user.setRealName(trim(request.getRealName(), 50));
        }
        if (request.getRole() != null) {
            user.setRole(UserRoleConstants.normalize(request.getRole()));
        }
        if (request.getDepartment() != null) {
            user.setDepartment(trim(request.getDepartment(), 100));
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(BCrypt.hashpw(request.getPassword()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toDto(user);
    }

    @Transactional
    public AdminUserDto updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return null;
        }
        user.setStatus(Integer.valueOf(1).equals(status) ? 1 : 0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toDto(user);
    }

    public boolean usernameExists(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }

    private AdminUserDto toDto(User user) {
        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRealName(),
                UserRoleConstants.normalize(user.getRole()),
                user.getDepartment(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private String trim(String value, int maxLength) {
        String next = value == null ? "" : value.trim();
        return next.length() <= maxLength ? next : next.substring(0, maxLength);
    }
}
