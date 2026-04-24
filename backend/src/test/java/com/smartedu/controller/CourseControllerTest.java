package com.smartedu.controller;

import com.smartedu.dto.CourseTeachingMaterialGroupDto;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.dto.MaterialVersionItemDto;
import com.smartedu.entity.Course;
import com.smartedu.service.CourseService;
import com.smartedu.service.TeachingMaterialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CourseController controller = new CourseController(new StubCourseService(), new StubTeachingMaterialService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnCourseTeachingMaterials() throws Exception {
        mockMvc.perform(get("/api/courses/9/materials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].parseTaskId").value(101))
                .andExpect(jsonPath("$.data[0].displayTitle").value("IoT Teaching Outline"))
                .andExpect(jsonPath("$.data[0].latestMaterialId").value(1001))
                .andExpect(jsonPath("$.data[0].versions[0].versionNo").value(2));
    }

    @Test
    void shouldCreateCourseFromStructuredRequest() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "IoT System Design",
                                  "code": "IOT301",
                                  "description": "Core major course",
                                  "semester": "2026 Spring",
                                  "teacherId": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("IoT System Design"))
                .andExpect(jsonPath("$.data.code").value("IOT301"))
                .andExpect(jsonPath("$.data.teacherId").value(7));
    }

    @Test
    void shouldRejectBlankCourseName() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Course name cannot be empty"));
    }

    @Test
    void shouldRejectMissingTeacherId() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "IoT System Design"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Teacher id cannot be empty"));
    }

    private static class StubCourseService extends CourseService {

        StubCourseService() {
            super(null, null, null, null);
        }

        @Override
        public List<KnowledgeNodeView> getCourseKnowledgePoints(Long courseId) {
            return List.of();
        }

        @Override
        public Course createCourse(String name, String code, String description, String semester, Long teacherId) {
            Course course = new Course();
            course.setId(501L);
            course.setName(name);
            course.setCode(code);
            course.setDescription(description);
            course.setSemester(semester);
            course.setTeacherId(teacherId);
            return course;
        }
    }

    private static class StubTeachingMaterialService extends TeachingMaterialService {

        StubTeachingMaterialService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public List<CourseTeachingMaterialGroupDto> getCourseMaterialGroups(Long courseId) {
            MaterialVersionItemDto latest = new MaterialVersionItemDto(1001L, 2, "PUBLISHED", 1, LocalDateTime.now());
            MaterialVersionItemDto history = new MaterialVersionItemDto(1000L, 1, "PUBLISHED", 0, LocalDateTime.now().minusDays(1));

            CourseTeachingMaterialGroupDto group = new CourseTeachingMaterialGroupDto();
            group.setParseTaskId(101L);
            group.setCourseId(courseId);
            group.setDisplayTitle("IoT Teaching Outline");
            group.setSourceFileName("iot-outline.docx");
            group.setLatestMaterialId(1001L);
            group.setLatestVersionNo(2);
            group.setLatestStatus("PUBLISHED");
            group.setUpdatedAt(LocalDateTime.now());
            group.setVersions(List.of(latest, history));
            return List.of(group);
        }
    }
}
