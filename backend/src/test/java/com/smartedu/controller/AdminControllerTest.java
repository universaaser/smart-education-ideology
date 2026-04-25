package com.smartedu.controller;

import com.smartedu.dto.AdminUserDto;
import com.smartedu.dto.AdminUserRequestDto;
import com.smartedu.dto.CourseStudentDto;
import com.smartedu.dto.PageResultDto;
import com.smartedu.service.AdminUserService;
import com.smartedu.service.CourseStudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(new StubAdminUserService(), new StubCourseStudentService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users?role=STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].username").value("student_chen"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldIgnoreInvalidRoleFilter() throws Exception {
        mockMvc.perform(get("/api/admin/users?role=bad-role"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].username").value("student_chen"));
    }

    @Test
    void shouldCreateUser() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student_new",
                                  "password": "123456",
                                  "email": "student@example.com",
                                  "realName": "Student New",
                                  "role": "STUDENT",
                                  "department": "IoT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("student_new"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    void shouldRejectShortPassword() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student_new",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Password must be at least 6 characters"));
    }

    @Test
    void shouldRejectInvalidRoleWhenCreatingUser() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student_new",
                                  "password": "123456",
                                  "role": "bad-role"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid user role"));
    }

    @Test
    void shouldRejectShortPasswordWhenUpdatingUser() throws Exception {
        mockMvc.perform(put("/api/admin/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Password must be at least 6 characters"));
    }

    @Test
    void shouldUpdateUserStatus() throws Exception {
        mockMvc.perform(put("/api/admin/users/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    void shouldBindStudentToCourse() throws Exception {
        mockMvc.perform(post("/api/admin/courses/9/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.courseId").value(9))
                .andExpect(jsonPath("$.data.studentId").value(3));
    }

    @Test
    void shouldListCourseStudents() throws Exception {
        mockMvc.perform(get("/api/admin/courses/9/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].username").value("student_chen"));
    }

    @Test
    void shouldRemoveCourseStudent() throws Exception {
        mockMvc.perform(delete("/api/admin/courses/9/students/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private static class StubAdminUserService extends AdminUserService {

        StubAdminUserService() {
            super(null);
        }

        @Override
        public PageResultDto<AdminUserDto> listUsers(String keyword, String role, int page, int size) {
            return new PageResultDto<>(List.of(user("student_chen", 1)), 1L, page, size);
        }

        @Override
        public AdminUserDto createUser(AdminUserRequestDto request) {
            return user(request.getUsername(), 1);
        }

        @Override
        public AdminUserDto updateUser(Long id, AdminUserRequestDto request) {
            return user("student_chen", 1);
        }

        @Override
        public AdminUserDto updateStatus(Long id, Integer status) {
            return user("student_chen", status);
        }

        @Override
        public boolean usernameExists(String username) {
            return false;
        }

        private AdminUserDto user(String username, Integer status) {
            return new AdminUserDto(3L, username, "student@example.com", "Student Chen", "STUDENT", "IoT", status, now(), now());
        }
    }

    private static class StubCourseStudentService extends CourseStudentService {

        StubCourseStudentService() {
            super(null, null, null);
        }

        @Override
        public List<CourseStudentDto> listCourseStudents(Long courseId) {
            return List.of(student(courseId, 3L));
        }

        @Override
        public CourseStudentDto addStudent(Long courseId, Long studentId) {
            return student(courseId, studentId);
        }

        @Override
        public boolean removeStudent(Long courseId, Long studentId) {
            return true;
        }

        private CourseStudentDto student(Long courseId, Long studentId) {
            return new CourseStudentDto(courseId, studentId, "student_chen", "Student Chen", "student@example.com", "IoT", now());
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.of(2026, 4, 25, 3, 10);
    }
}
