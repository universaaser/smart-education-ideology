package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.MatchReviewDto;
import com.smartedu.dto.MatchReviewHistoryDto;
import com.smartedu.dto.MatchReviewUpdateRequestDto;
import com.smartedu.service.MatchReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchReviewController {

    private static final List<String> REVIEW_STATUSES = List.of("PENDING", "APPROVED", "REJECTED");

    private final MatchReviewService matchReviewService;

    @GetMapping("/pending")
    public Result<List<MatchReviewDto>> listPendingMatches(@RequestParam(required = false) String status) {
        String nextStatus = status == null ? "PENDING" : status.trim();
        if (!nextStatus.isEmpty() && !REVIEW_STATUSES.contains(nextStatus)) {
            return Result.badRequest("Invalid review status");
        }
        return Result.success(matchReviewService.listMatches(nextStatus));
    }

    @GetMapping("/{id}/history")
    public Result<List<MatchReviewHistoryDto>> listHistory(@PathVariable Long id) {
        return Result.success(matchReviewService.listHistory(id));
    }

    @PutMapping("/{id}/approve")
    public Result<MatchReviewDto> approve(@PathVariable Long id, @RequestBody(required = false) MatchReviewUpdateRequestDto request) {
        MatchReviewDto match = matchReviewService.approve(id, request);
        if (match == null) {
            return Result.notFound("Match not found");
        }
        return Result.success(match);
    }

    @PutMapping("/{id}/reject")
    public Result<MatchReviewDto> reject(@PathVariable Long id, @RequestBody(required = false) MatchReviewUpdateRequestDto request) {
        MatchReviewDto match = matchReviewService.reject(id, request);
        if (match == null) {
            return Result.notFound("Match not found");
        }
        return Result.success(match);
    }

    @PostMapping("/{id}/revise")
    public Result<MatchReviewDto> revise(@PathVariable Long id, @RequestBody MatchReviewUpdateRequestDto request) {
        if (request == null || request.getMatchReason() == null || request.getMatchReason().trim().isEmpty()) {
            return Result.badRequest("Match reason cannot be empty");
        }
        MatchReviewDto match = matchReviewService.revise(id, request);
        if (match == null) {
            return Result.notFound("Match not found");
        }
        return Result.success(match);
    }
}
