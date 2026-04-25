package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.CourseChapterDto;
import com.smartedu.dto.CourseChapterRequestDto;
import com.smartedu.dto.CourseStatusSummaryDto;
import com.smartedu.dto.CourseUpdateRequestDto;
import com.smartedu.dto.KnowledgeNodeView;
import com.smartedu.entity.Course;
import com.smartedu.entity.CourseChapter;
import com.smartedu.entity.CourseSubjectKnowledge;
import com.smartedu.entity.ParseTask;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.entity.TeachingMaterial;
import com.smartedu.mapper.CourseChapterMapper;
import com.smartedu.mapper.CourseMapper;
import com.smartedu.mapper.CourseSubjectKnowledgeMapper;
import com.smartedu.mapper.ParseTaskMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import com.smartedu.mapper.TeachingMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final CourseChapterMapper courseChapterMapper;
    private final TeachingMaterialMapper teachingMaterialMapper;
    private final ParseTaskMapper parseTaskMapper;

    /**
     * 获取课程关联的学科知识列表。
     *
     * <p>
     * 返回结构仍然保持图谱节点形状，避免前端课程页额外改协议。
     */
    public Course getCourseById(Long courseId) {
        return courseMapper.selectById(courseId);
    }

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


    public List<CourseChapterDto> getCourseChapters(Long courseId) {
        LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseChapter::getCourseId, courseId)
                .orderByAsc(CourseChapter::getSortOrder)
                .orderByAsc(CourseChapter::getId);
        return courseChapterMapper.selectList(wrapper).stream()
                .map(this::toChapterDto)
                .toList();
    }

    @Transactional
    public CourseChapterDto createCourseChapter(Long courseId, CourseChapterRequestDto request) {
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(courseId);
        chapter.setParentId(request == null ? null : request.getParentId());
        chapter.setTitle(trimToLength(request == null ? "" : request.getTitle(), 200));
        chapter.setSortOrder(request == null || request.getSortOrder() == null ? 0 : request.getSortOrder());
        chapter.setCreatedAt(LocalDateTime.now());
        chapter.setUpdatedAt(LocalDateTime.now());
        courseChapterMapper.insert(chapter);
        return toChapterDto(chapter);
    }

    @Transactional
    public CourseChapterDto updateCourseChapter(Long courseId, Long chapterId, CourseChapterRequestDto request) {
        CourseChapter chapter = courseChapterMapper.selectById(chapterId);
        if (chapter == null || !courseId.equals(chapter.getCourseId())) {
            return null;
        }
        chapter.setParentId(request == null ? null : request.getParentId());
        chapter.setTitle(trimToLength(request == null ? "" : request.getTitle(), 200));
        chapter.setSortOrder(request == null || request.getSortOrder() == null ? 0 : request.getSortOrder());
        chapter.setUpdatedAt(LocalDateTime.now());
        courseChapterMapper.updateById(chapter);
        return toChapterDto(chapter);
    }

    @Transactional
    public boolean bindMaterialToChapter(Long courseId, Long chapterId, Long materialId) {
        CourseChapter chapter = courseChapterMapper.selectById(chapterId);
        TeachingMaterial material = teachingMaterialMapper.selectById(materialId);
        if (chapter == null || material == null) {
            return false;
        }
        if (!courseId.equals(chapter.getCourseId()) || !courseId.equals(material.getCourseId())) {
            return false;
        }
        material.setChapterId(chapterId);
        material.setUpdatedAt(LocalDateTime.now());
        teachingMaterialMapper.updateById(material);
        return true;
    }

    @Transactional
    public Course updateCourse(Long courseId, CourseUpdateRequestDto request) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            return null;
        }
        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Course name cannot be empty");
            }
            course.setName(name);
        }
        if (request.getCode() != null) {
            course.setCode(request.getCode().trim());
        }
        if (request.getDescription() != null) {
            course.setDescription(request.getDescription().trim());
        }
        if (request.getTeacherId() != null) {
            course.setTeacherId(request.getTeacherId());
        }
        if (request.getSemester() != null) {
            course.setSemester(request.getSemester().trim());
        }
        if (request.getProgress() != null) {
            course.setProgress(Math.max(0, Math.min(100, request.getProgress())));
        }
        if (request.getIdeologyScore() != null) {
            course.setIdeologyScore(request.getIdeologyScore().trim());
        }
        if (request.getCoverImage() != null) {
            course.setCoverImage(request.getCoverImage().trim());
        }
        if (request.getStatus() != null) {
            course.setStatus(request.getStatus());
        }
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
        return course;
    }

    public CourseStatusSummaryDto getCourseStatusSummary(Long courseId) {
        long chapterCount = courseChapterMapper.selectCount(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, courseId));
        long parseTaskCount = parseTaskMapper.selectCount(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getCourseId, courseId));
        long materialCount = teachingMaterialMapper.selectCount(new LambdaQueryWrapper<TeachingMaterial>()
                .eq(TeachingMaterial::getCourseId, courseId));
        long draftMaterialCount = teachingMaterialMapper.selectCount(new LambdaQueryWrapper<TeachingMaterial>()
                .eq(TeachingMaterial::getCourseId, courseId)
                .eq(TeachingMaterial::getStatus, "DRAFT"));
        long publishedMaterialCount = teachingMaterialMapper.selectCount(new LambdaQueryWrapper<TeachingMaterial>()
                .eq(TeachingMaterial::getCourseId, courseId)
                .eq(TeachingMaterial::getStatus, "PUBLISHED"));
        long knowledgePointCount = courseSubjectKnowledgeMapper.selectCount(new LambdaQueryWrapper<CourseSubjectKnowledge>()
                .eq(CourseSubjectKnowledge::getCourseId, courseId));
        return new CourseStatusSummaryDto(
                chapterCount,
                parseTaskCount,
                materialCount,
                draftMaterialCount,
                publishedMaterialCount,
                knowledgePointCount);
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
        if (matches == null || matches.isEmpty()) {
            return;
        }

        List<CourseSubjectKnowledge> existingLinks = loadExistingCourseLinks(course.getId());
        Set<Long> existingSubjectKnowledgeIds = new HashSet<>();
        int sortOrder = 1;
        for (CourseSubjectKnowledge existingLink : existingLinks) {
            if (existingLink.getSubjectKnowledgeId() != null) {
                existingSubjectKnowledgeIds.add(existingLink.getSubjectKnowledgeId());
            }
            if (existingLink.getSortOrder() != null) {
                sortOrder = Math.max(sortOrder, existingLink.getSortOrder() + 1);
            }
        }

        for (SubjectKnowledge match : matches) {
            if (match == null || match.getId() == null || existingSubjectKnowledgeIds.contains(match.getId())) {
                continue;
            }

            CourseSubjectKnowledge link = new CourseSubjectKnowledge();
            link.setCourseId(course.getId());
            link.setSubjectKnowledgeId(match.getId());
            link.setSortOrder(sortOrder++);
            link.setCreatedAt(LocalDateTime.now());
            link.setUpdatedAt(LocalDateTime.now());
            courseSubjectKnowledgeMapper.insert(link);
            existingSubjectKnowledgeIds.add(match.getId());
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

    private List<CourseSubjectKnowledge> loadExistingCourseLinks(Long courseId) {
        LambdaQueryWrapper<CourseSubjectKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseSubjectKnowledge::getCourseId, courseId);
        return courseSubjectKnowledgeMapper.selectList(wrapper);
    }

    private CourseChapterDto toChapterDto(CourseChapter chapter) {
        return new CourseChapterDto(
                chapter.getId(),
                chapter.getCourseId(),
                chapter.getParentId(),
                chapter.getTitle(),
                chapter.getSortOrder(),
                chapter.getUpdatedAt());
    }

    private String trimToLength(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
