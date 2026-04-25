package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.AdminUserDto;
import com.smartedu.dto.AdminUserRequestDto;
import com.smartedu.dto.AdminUserStatusRequestDto;
import com.smartedu.dto.CourseStudentDto;
import com.smartedu.dto.CourseStudentRequestDto;
import com.smartedu.dto.PageResultDto;
import com.smartedu.service.AdminUserService;
import com.smartedu.service.CourseStudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Set<String> SUPPORTED_ROLES = Set.of("TEACHER", "STUDENT", "ADMIN");

    private final AdminUserService adminUserService;
    private final CourseStudentService courseStudentService;

    @GetMapping("/users")
    public Result<PageResultDto<AdminUserDto>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminUserService.listUsers(keyword, role, page, size));
    }

    @PostMapping("/users")
    public Result<AdminUserDto> createUser(@RequestBody AdminUserRequestDto request) {
        String username = request == null || request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request == null || request.getPassword() == null ? "" : request.getPassword();
        if (username.isEmpty()) {
            return Result.badRequest("Username cannot be empty");
        }
        if (password.length() < 6) {
            return Result.badRequest("Password must be at least 6 characters");
        }
        if (!isSupportedRole(request.getRole())) {
            return Result.badRequest("Invalid user role");
        }
        if (adminUserService.usernameExists(username)) {
            return Result.badRequest("Username already exists");
        }
        return Result.success(adminUserService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public Result<AdminUserDto> updateUser(@PathVariable Long id, @RequestBody AdminUserRequestDto request) {
        if (request == null) {
            return Result.badRequest("Request body cannot be empty");
        }
        if (request.getPassword() != null && !request.getPassword().isBlank() && request.getPassword().length() < 6) {
            return Result.badRequest("Password must be at least 6 characters");
        }
        if (!isSupportedRole(request.getRole())) {
            return Result.badRequest("Invalid user role");
        }
        AdminUserDto user = adminUserService.updateUser(id, request);
        if (user == null) {
            return Result.notFound("User not found");
        }
        return Result.success(user);
    }

    @PutMapping("/users/{id}/status")
    public Result<AdminUserDto> updateStatus(@PathVariable Long id, @RequestBody AdminUserStatusRequestDto request) {
        if (request == null || request.getStatus() == null) {
            return Result.badRequest("User status cannot be empty");
        }
        AdminUserDto user = adminUserService.updateStatus(id, request.getStatus());
        if (user == null) {
            return Result.notFound("User not found");
        }
        return Result.success(user);
    }

    @GetMapping("/courses/{id}/students")
    public Result<List<CourseStudentDto>> listCourseStudents(@PathVariable Long id) {
        return Result.success(courseStudentService.listCourseStudents(id));
    }

    @PostMapping("/courses/{id}/students")
    public Result<CourseStudentDto> addCourseStudent(
            @PathVariable Long id,
            @RequestBody CourseStudentRequestDto request) {
        if (request == null || request.getStudentId() == null) {
            return Result.badRequest("Student id cannot be empty");
        }
        CourseStudentDto student = courseStudentService.addStudent(id, request.getStudentId());
        if (student == null) {
            return Result.badRequest("Invalid course or student");
        }
        return Result.success(student);
    }

    @DeleteMapping("/courses/{id}/students/{studentId}")
    public Result<Void> removeCourseStudent(@PathVariable Long id, @PathVariable Long studentId) {
        courseStudentService.removeStudent(id, studentId);
        return Result.success();
    }

    private boolean isSupportedRole(String role) {
        return role == null || role.isBlank() || SUPPORTED_ROLES.contains(role.trim().toUpperCase());
    }
}
