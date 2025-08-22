package com.words_hanjoom.domain.feedback.controller;

import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackThisMonthActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbacksDto;
import com.words_hanjoom.domain.feedback.service.FeedbackService;
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

    @GetMapping("/list")
    public ResponseEntity<?> getUserFeedbackList(
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestParam("day") int day
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof UsernamePasswordAuthenticationToken customAuth) {
                String loginId = customAuth.getName();
                System.out.println("/api/feedback/{articleId} API 요청 유저 이메일: " + loginId);
            }
            Map<String, List<FeedbackThisMonthActivityDto>> result = feedbackService.getUserActivitiesThisMonth(auth.getName(), year, month, day);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/{articleId}")
    public ResponseEntity<?> getFeedback(@PathVariable("articleId") long articleId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof UsernamePasswordAuthenticationToken customAuth) {
                String loginId = customAuth.getName();
                System.out.println("/api/feedback/{articleId} API 요청 유저 이메일: " + loginId);
            }
            FeedbacksDto result = feedbackService.getScrapActivityRecord(auth.getName(), articleId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> feedback(@RequestBody ScrapActivityDto activity) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof UsernamePasswordAuthenticationToken customAuth) {
                String loginId = customAuth.getName();
                System.out.println("/api/feedback/{articleId} API 요청 유저 이메일: " + loginId);
            }
            FeedbacksDto result = feedbackService.feedbackScrapActivity(auth.getName(), activity);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("서버가 잘못했습니다.");
        }
    }
}
