package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.entity.Course;
import com.smartedu.entity.CourseSubjectKnowledge;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.mapper.CourseMapper;
import com.smartedu.mapper.CourseSubjectKnowledgeMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程服务
 *
 * <p>
 * 课程只关联学科知识，不直接挂思政知识。
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final CourseSubjectKnowledgeMapper courseSubjectKnowledgeMapper;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    /**
     * 获取课程关联的学科知识列表。
     *
     * <p>
     * 返回结构仍然保持图谱节点形状，避免前端课程页额外改协议。
     */
    public List<KnowledgeNodeView> getCourseKnowledgePoints(Long courseId) {
        ensureAutoAssociations(courseId);

        LambdaQueryWrapper<CourseSubjectKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseSubjectKnowledge::getCourseId, courseId)
                .orderByAsc(CourseSubjectKnowledge::getSortOrder)
                .orderByAsc(CourseSubjectKnowledge::getId);
        List<CourseSubjectKnowledge> links = courseSubjectKnowledgeMapper.selectList(wrapper);
        if (links.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> subjectKnowledgeIds = links.stream()
                .map(CourseSubjectKnowledge::getSubjectKnowledgeId)
                .toList();
        List<SubjectKnowledge> subjects = subjectKnowledgeMapper.selectBatchIds(subjectKnowledgeIds);
        Map<Long, SubjectKnowledge> subjectMap = new HashMap<>();
        for (SubjectKnowledge subject : subjects) {
            subjectMap.put(subject.getId(), subject);
        }

        List<KnowledgeNodeView> ordered = new ArrayList<>();
        for (Long subjectKnowledgeId : subjectKnowledgeIds) {
            SubjectKnowledge subject = subjectMap.get(subjectKnowledgeId);
            if (subject != null) {
                ordered.add(KnowledgeViewMapper.toSubjectNode(subject));
            }
        }
        return ordered;
    }

    /**
     * 创建新课程
     */
    @Transactional
    public Course createCourse(String name, String code, String description,
            String semester, Long teacherId) {
        Course course = new Course();
        course.setName(name);
        course.setCode(code);
        course.setDescription(description);
        course.setSemester(semester);
        course.setTeacherId(teacherId);
        course.setProgress(0);
        course.setIdeologyScore("FAIR");
        course.setStatus(1);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());

        courseMapper.insert(course);
        autoAssociateSubjectKnowledge(course);
        return course;
    }

    /**
     * 新课程创建后按课程名和描述做一次轻量自动关联。
     */
    private void autoAssociateSubjectKnowledge(Course course) {
        if (course == null || course.getId() == null) {
            return;
        }

        String query = String.join(" ", List.of(
                course.getName() == null ? "" : course.getName(),
                course.getDescription() == null ? "" : course.getDescription())).trim();
        if (query.isBlank()) {
            return;
        }

        List<SubjectKnowledge> matches = knowledgeRetrievalService.findRelevantSubjectKnowledge(query, 5);
        int sortOrder = 1;
        for (SubjectKnowledge match : matches) {
            LambdaQueryWrapper<CourseSubjectKnowledge> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(CourseSubjectKnowledge::getCourseId, course.getId())
                    .eq(CourseSubjectKnowledge::getSubjectKnowledgeId, match.getId())
                    .last("LIMIT 1");
            if (courseSubjectKnowledgeMapper.selectOne(wrapper) != null) {
                continue;
            }

            CourseSubjectKnowledge link = new CourseSubjectKnowledge();
            link.setCourseId(course.getId());
            link.setSubjectKnowledgeId(match.getId());
            link.setSortOrder(sortOrder++);
            link.setCreatedAt(LocalDateTime.now());
            link.setUpdatedAt(LocalDateTime.now());
            courseSubjectKnowledgeMapper.insert(link);
        }
    }

    /**
     * 历史课程首次读取时自动补齐课程-学科知识关联。
     */
    private void ensureAutoAssociations(Long courseId) {
        LambdaQueryWrapper<CourseSubjectKnowledge> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(CourseSubjectKnowledge::getCourseId, courseId).last("LIMIT 1");
        if (courseSubjectKnowledgeMapper.selectOne(existingWrapper) != null) {
            return;
        }

        Course course = courseMapper.selectById(courseId);
        autoAssociateSubjectKnowledge(course);
    }
}
