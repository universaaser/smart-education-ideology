package com.smartedu.controller;

import com.smartedu.dto.CourseChapterDto;
import com.smartedu.dto.CourseChapterRequestDto;
import com.smartedu.dto.CourseStatusSummaryDto;
import com.smartedu.dto.CourseTeachingMaterialGroupDto;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.dto.StudentCourseDto;
import com.smartedu.dto.MaterialVersionItemDto;
import com.smartedu.entity.Course;
import com.smartedu.service.CourseService;
import com.smartedu.service.CourseStudentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CourseController controller = new CourseController(new StubCourseService(), new StubTeachingMaterialService(), new StubCourseStudentService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnStudentCourses() throws Exception {
        mockMvc.perform(get("/api/courses/student/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(9))
                .andExpect(jsonPath("$.data[0].name").value("IoT System Design"));
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
    void shouldReturnCourseChapters() throws Exception {
        mockMvc.perform(get("/api/courses/9/chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(301))
                .andExpect(jsonPath("$.data[0].title").value("Chapter One"));
    }

    @Test
    void shouldCreateCourseChapter() throws Exception {
        mockMvc.perform(post("/api/courses/9/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Chapter Two",
                                  "sortOrder": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Chapter Two"));
    }

    @Test
    void shouldUpdateCourseChapter() throws Exception {
        mockMvc.perform(put("/api/courses/9/chapters/301")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Chapter Updated",
                                  "sortOrder": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Chapter Updated"));
    }

    @Test
    void shouldBindMaterialToChapter() throws Exception {
        mockMvc.perform(post("/api/courses/9/chapters/301/materials/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bound").value(true));
    }

    @Test
    void shouldReturnCourseStatusSummary() throws Exception {
        mockMvc.perform(get("/api/courses/9/status-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chapterCount").value(2))
                .andExpect(jsonPath("$.data.materialCount").value(4))
                .andExpect(jsonPath("$.data.knowledgePointCount").value(3));
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
            super(null, null, null, null, null, null, null);
        }

        @Override
        public List<KnowledgeNodeView> getCourseKnowledgePoints(Long courseId) {
            return List.of();
        }

        @Override
        public List<CourseChapterDto> getCourseChapters(Long courseId) {
            return List.of(new CourseChapterDto(301L, courseId, null, "Chapter One", 1, LocalDateTime.now()));
        }

        @Override
        public CourseChapterDto createCourseChapter(Long courseId, CourseChapterRequestDto request) {
            return new CourseChapterDto(302L, courseId, request.getParentId(), request.getTitle(), request.getSortOrder(), LocalDateTime.now());
        }

        @Override
        public CourseChapterDto updateCourseChapter(Long courseId, Long chapterId, CourseChapterRequestDto request) {
            return new CourseChapterDto(chapterId, courseId, request.getParentId(), request.getTitle(), request.getSortOrder(), LocalDateTime.now());
        }

        @Override
        public boolean bindMaterialToChapter(Long courseId, Long chapterId, Long materialId) {
            return true;
        }

        @Override
        public CourseStatusSummaryDto getCourseStatusSummary(Long courseId) {
            return new CourseStatusSummaryDto(2, 5, 4, 1, 3, 3);
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

    private static class StubCourseStudentService extends CourseStudentService {

        StubCourseStudentService() {
            super(null, null, null);
        }

        @Override
        public List<StudentCourseDto> listStudentCourses(Long studentId) {
            return List.of(new StudentCourseDto(9L, "IoT System Design", 75, "GOOD", "blue", "Good", "IOT301", "Core major course", "2026 Spring"));
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
            group.setChapterId(301L);
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
