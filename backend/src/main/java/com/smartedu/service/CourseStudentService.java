package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.constant.UserRoleConstants;
import com.smartedu.dto.CourseStudentDto;
import com.smartedu.dto.StudentCourseDto;
import com.smartedu.dto.StudentOptionDto;
import com.smartedu.entity.Course;
import com.smartedu.entity.CourseStudent;
import com.smartedu.entity.User;
import com.smartedu.mapper.CourseMapper;
import com.smartedu.mapper.CourseStudentMapper;
import com.smartedu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CourseStudentService {

    private final CourseStudentMapper courseStudentMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;

    public List<StudentOptionDto> listStudentOptions() {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRoleConstants.STUDENT)
                .eq(User::getStatus, 1)
                .orderByAsc(User::getUsername)).stream()
                .map(user -> new StudentOptionDto(
                        user.getId(),
                        user.getUsername(),
                        user.getRealName(),
                        user.getEmail(),
                        user.getDepartment()))
                .toList();
    }

    public List<CourseStudentDto> listCourseStudents(Long courseId) {
        List<CourseStudent> links = courseStudentMapper.selectList(new LambdaQueryWrapper<CourseStudent>()
                .eq(CourseStudent::getCourseId, courseId)
                .orderByDesc(CourseStudent::getCreatedAt));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = links.stream().map(CourseStudent::getStudentId).toList();
        Map<Long, User> students = new LinkedHashMap<>();
        for (User user : userMapper.selectBatchIds(studentIds)) {
            students.put(user.getId(), user);
        }
        List<CourseStudentDto> result = new ArrayList<>();
        for (CourseStudent link : links) {
            User user = students.get(link.getStudentId());
            if (user != null) {
                result.add(toCourseStudentDto(link.getCourseId(), user, link.getCreatedAt()));
            }
        }
        return result;
    }

    @Transactional
    public CourseStudentDto addStudent(Long courseId, Long studentId) {
        Course course = courseMapper.selectById(courseId);
        User student = userMapper.selectById(studentId);
        if (course == null || student == null || !UserRoleConstants.STUDENT.equals(UserRoleConstants.normalize(student.getRole()))) {
            return null;
        }
        CourseStudent existing = courseStudentMapper.selectOne(new LambdaQueryWrapper<CourseStudent>()
                .eq(CourseStudent::getCourseId, courseId)
                .eq(CourseStudent::getStudentId, studentId)
                .last("LIMIT 1"));
        if (existing != null) {
            return toCourseStudentDto(courseId, student, existing.getCreatedAt());
        }
        CourseStudent link = new CourseStudent();
        link.setCourseId(courseId);
        link.setStudentId(studentId);
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        courseStudentMapper.insert(link);
        return toCourseStudentDto(courseId, student, link.getCreatedAt());
    }

    @Transactional
    public boolean removeStudent(Long courseId, Long studentId) {
        return courseStudentMapper.delete(new LambdaQueryWrapper<CourseStudent>()
                .eq(CourseStudent::getCourseId, courseId)
                .eq(CourseStudent::getStudentId, studentId)) > 0;
    }

    public List<StudentCourseDto> listStudentCourses(Long studentId) {
        List<CourseStudent> links = courseStudentMapper.selectList(new LambdaQueryWrapper<CourseStudent>()
                .eq(CourseStudent::getStudentId, studentId)
                .orderByDesc(CourseStudent::getCreatedAt));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = links.stream().map(CourseStudent::getCourseId).toList();
        Map<Long, Course> courses = new LinkedHashMap<>();
        for (Course course : courseMapper.selectBatchIds(courseIds)) {
            courses.put(course.getId(), course);
        }
        List<StudentCourseDto> result = new ArrayList<>();
        for (Long courseId : courseIds) {
            Course course = courses.get(courseId);
            if (course != null) {
                result.add(toStudentCourseDto(course));
            }
        }
        return result;
    }

    private CourseStudentDto toCourseStudentDto(Long courseId, User user, LocalDateTime boundAt) {
        return new CourseStudentDto(
                courseId,
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getEmail(),
                user.getDepartment(),
                boundAt);
    }

    private StudentCourseDto toStudentCourseDto(Course course) {
        return new StudentCourseDto(
                course.getId(),
                course.getName(),
                course.getProgress(),
                course.getIdeologyScore(),
                mapGradeColor(course.getIdeologyScore()),
                mapGradeLabel(course.getIdeologyScore()),
                course.getCode(),
                course.getDescription(),
                course.getSemester());
    }

    private String mapGradeColor(String ideologyScore) {
        return switch (ideologyScore == null ? "" : ideologyScore) {
            case "EXCELLENT" -> "green";
            case "GOOD" -> "blue";
            case "FAIR" -> "orange";
            default -> "slate";
        };
    }

    private String mapGradeLabel(String ideologyScore) {
        return switch (ideologyScore == null ? "" : ideologyScore) {
            case "EXCELLENT" -> "Excellent";
            case "GOOD" -> "Good";
            case "FAIR" -> "General";
            default -> "Pending";
        };
    }
}
