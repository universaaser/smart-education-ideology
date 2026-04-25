package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.dto.MatchReviewDto;
import com.smartedu.dto.MatchReviewHistoryDto;
import com.smartedu.dto.MatchReviewUpdateRequestDto;
import com.smartedu.entity.IdeologyKnowledge;
import com.smartedu.entity.SubjectIdeologyMatch;
import com.smartedu.entity.SubjectIdeologyMatchReview;
import com.smartedu.entity.SubjectKnowledge;
import com.smartedu.mapper.IdeologyKnowledgeMapper;
import com.smartedu.mapper.SubjectIdeologyMatchMapper;
import com.smartedu.mapper.SubjectIdeologyMatchReviewMapper;
import com.smartedu.mapper.SubjectKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchReviewService {

    private static final List<String> REVIEW_STATUSES = List.of("PENDING", "APPROVED", "REJECTED");

    private final SubjectIdeologyMatchMapper matchMapper;
    private final SubjectIdeologyMatchReviewMapper reviewMapper;
    private final SubjectKnowledgeMapper subjectKnowledgeMapper;
    private final IdeologyKnowledgeMapper ideologyKnowledgeMapper;

    public List<MatchReviewDto> listMatches(String status) {
        LambdaQueryWrapper<SubjectIdeologyMatch> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(SubjectIdeologyMatch::getReviewStatus, status.trim());
        }
        wrapper.orderByDesc(SubjectIdeologyMatch::getCreatedAt).orderByDesc(SubjectIdeologyMatch::getId);
        return matchMapper.selectList(wrapper).stream().map(this::toDto).toList();
    }

    public List<MatchReviewHistoryDto> listHistory(Long matchId) {
        LambdaQueryWrapper<SubjectIdeologyMatchReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectIdeologyMatchReview::getMatchId, matchId).orderByDesc(SubjectIdeologyMatchReview::getCreatedAt);
        return reviewMapper.selectList(wrapper).stream().map(this::toHistoryDto).toList();
    }

    @Transactional
    public MatchReviewDto approve(Long id, MatchReviewUpdateRequestDto request) {
        return updateReview(id, "APPROVE", "APPROVED", request);
    }

    @Transactional
    public MatchReviewDto reject(Long id, MatchReviewUpdateRequestDto request) {
        return updateReview(id, "REJECT", "REJECTED", request);
    }

    @Transactional
    public MatchReviewDto revise(Long id, MatchReviewUpdateRequestDto request) {
        if (request == null || request.getMatchReason() == null || request.getMatchReason().trim().isEmpty()) {
            return null;
        }
        SubjectIdeologyMatch match = matchMapper.selectById(id);
        if (match == null) {
            return null;
        }
        String previousStatus = normalizeStatus(match.getReviewStatus());
        String previousReason = match.getMatchReason();
        match.setMatchReason(trim(request.getMatchReason(), 1000));
        match.setReviewStatus(previousStatus);
        match.setReviewComment(trim(request.getReviewComment(), 500));
        match.setReviewerId(request.getReviewerId());
        match.setReviewedAt(LocalDateTime.now());
        match.setVersion(nextVersion(match));
        matchMapper.updateById(match);
        recordHistory(match, "REVISE", previousStatus, previousStatus, previousReason, match.getMatchReason(), request);
        return toDto(match);
    }

    private MatchReviewDto updateReview(Long id, String action, String nextStatus, MatchReviewUpdateRequestDto request) {
        SubjectIdeologyMatch match = matchMapper.selectById(id);
        if (match == null) {
            return null;
        }
        String previousStatus = normalizeStatus(match.getReviewStatus());
        String previousReason = match.getMatchReason();
        if (request != null && request.getMatchReason() != null && !request.getMatchReason().trim().isEmpty()) {
            match.setMatchReason(trim(request.getMatchReason(), 1000));
        }
        match.setReviewStatus(nextStatus);
        match.setReviewComment(trim(request == null ? null : request.getReviewComment(), 500));
        match.setReviewerId(request == null ? null : request.getReviewerId());
        match.setReviewedAt(LocalDateTime.now());
        match.setVersion(nextVersion(match));
        matchMapper.updateById(match);
        recordHistory(match, action, previousStatus, nextStatus, previousReason, match.getMatchReason(), request);
        return toDto(match);
    }

    private void recordHistory(
            SubjectIdeologyMatch match,
            String action,
            String previousStatus,
            String nextStatus,
            String previousReason,
            String nextReason,
            MatchReviewUpdateRequestDto request) {
        SubjectIdeologyMatchReview review = new SubjectIdeologyMatchReview();
        review.setMatchId(match.getId());
        review.setAction(action);
        review.setPreviousStatus(previousStatus);
        review.setNextStatus(nextStatus);
        review.setPreviousReason(trim(previousReason, 1000));
        review.setNextReason(trim(nextReason, 1000));
        review.setReviewerId(request == null ? null : request.getReviewerId());
        review.setReviewComment(trim(request == null ? null : request.getReviewComment(), 500));
        review.setVersion(match.getVersion());
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);
    }

    private MatchReviewDto toDto(SubjectIdeologyMatch match) {
        SubjectKnowledge subjectKnowledge = subjectKnowledgeMapper.selectById(match.getSubjectKnowledgeId());
        IdeologyKnowledge ideologyKnowledge = ideologyKnowledgeMapper.selectById(match.getIdeologyKnowledgeId());
        return new MatchReviewDto(
                match.getId(),
                match.getSubjectKnowledgeId(),
                subjectKnowledge == null ? "Unknown knowledge" : subjectKnowledge.getName(),
                subjectKnowledge == null ? "" : subjectKnowledge.getSubject(),
                subjectKnowledge == null ? "" : subjectKnowledge.getCategory(),
                match.getIdeologyKnowledgeId(),
                ideologyKnowledge == null ? "Unknown ideology" : ideologyKnowledge.getName(),
                ideologyKnowledge == null ? "" : ideologyKnowledge.getDescription(),
                match.getIsPrimary(),
                match.getMatchScore(),
                match.getMatchReason(),
                normalizeStatus(match.getReviewStatus()),
                match.getVersion() == null ? 1 : match.getVersion(),
                match.getReviewerId(),
                match.getReviewedAt(),
                match.getReviewComment(),
                match.getCreatedAt());
    }

    private MatchReviewHistoryDto toHistoryDto(SubjectIdeologyMatchReview review) {
        return new MatchReviewHistoryDto(
                review.getId(),
                review.getMatchId(),
                review.getAction(),
                review.getPreviousStatus(),
                review.getNextStatus(),
                review.getPreviousReason(),
                review.getNextReason(),
                review.getReviewerId(),
                review.getReviewComment(),
                review.getVersion(),
                review.getCreatedAt());
    }

    private int nextVersion(SubjectIdeologyMatch match) {
        return (match.getVersion() == null ? 1 : match.getVersion()) + 1;
    }

    private String normalizeStatus(String status) {
        return REVIEW_STATUSES.contains(status) ? status : "PENDING";
    }

    private String trim(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
