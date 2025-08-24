package com.words_hanjoom.domain.feedback.controller;

import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackListDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackThisMonthActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbacksDto;
import com.words_hanjoom.domain.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    @Autowired
    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "월별 신문 스크랩 활동 기록 조회")
    @GetMapping("/list")
    public ResponseEntity<FeedbackListDto> getUserFeedbackList(
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestParam("day") int day
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UsernamePasswordAuthenticationToken customAuth) {
            String loginId = customAuth.getName();
            System.out.println("/api/feedback/{articleId} API 요청 유저 이메일: " + loginId);
        }
        FeedbackListDto result = feedbackService.getUserActivitiesThisMonth(auth.getName(), year, month, day);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "특정 신문 스크랩 활동 피드백 조회")
    @GetMapping("/{articleId}")
    public ResponseEntity<FeedbacksDto> getFeedback(@PathVariable("articleId") long articleId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UsernamePasswordAuthenticationToken customAuth) {
            String loginId = customAuth.getName();
            System.out.println("/api/feedback/{articleId} API 요청 유저 이메일: " + loginId);
        }
        FeedbacksDto result = feedbackService.getScrapActivityRecord(auth.getName(), articleId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "신문 스크랩 활동 피드백 처리")
    @PostMapping
    public ResponseEntity<FeedbacksDto> feedback(@RequestBody ScrapActivityDto activity) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UsernamePasswordAuthenticationToken customAuth) {
            String loginId = customAuth.getName();
            System.out.println("/api/feedback/{articleId} API 요청 유저 이메일: " + loginId);
        }
        FeedbacksDto result = feedbackService.feedbackScrapActivity(auth.getName(), activity);
        return ResponseEntity.ok(result);
    }
}
