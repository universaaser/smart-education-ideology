package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.entity.User;
import com.smartedu.mapper.UserMapper;
import com.smartedu.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户控制器
 * 
 * <p>
 * 处理用户信息更新（如头像上传）
 * 
 * @author SmartEducation Team
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * 上传用户头像
     * 
     * @param file          头像图片
     * @param authorization 认证令牌
     * @return 头像 URL
     */
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.unauthorized("未提供有效的令牌");
        }

        String token = authorization.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return Result.unauthorized("令牌无效");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);

        if (file.isEmpty()) {
            return Result.badRequest("请选择头像文件");
        }

        try {
            // 确保上传目录存在
            Path uploadPath = Paths.get(uploadDir, "avatars");
            Files.createDirectories(uploadPath);

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".png";
            String newFilename = "avatar_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            // 保存文件
            Path filePath = uploadPath.resolve(newFilename);
            file.transferTo(filePath.toFile());

            // 更新用户头像 URL
            String avatarUrl = "/uploads/avatars/" + newFilename;
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setAvatar(avatarUrl);
                userMapper.updateById(user);
            }

            Map<String, String> result = new HashMap<>();
            result.put("avatarUrl", avatarUrl);
            return Result.success("头像上传成功", result);

        } catch (IOException e) {
            return Result.error("头像上传失败: " + e.getMessage());
        }
    }
}
