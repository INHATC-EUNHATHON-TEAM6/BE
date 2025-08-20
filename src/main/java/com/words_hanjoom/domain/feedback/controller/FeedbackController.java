package com.words_hanjoom.domain.feedback.controller;

import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackThisMonthActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbacksDto;
import com.words_hanjoom.domain.feedback.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FeedbackController {
    private final FeedbackService feedbackService;

    @Autowired
    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/feedbacks")
    public ResponseEntity<?> getUserFeedbackList(
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestParam("day") int day
    ) {
        try {
            Map<String, List<FeedbackThisMonthActivityDto>> result = feedbackService.getUserActivitiesThisMonth(year, month, day);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestBody ScrapActivityDto activity) {
        try {
            FeedbacksDto result = feedbackService.feedbackScrapActivity(activity);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("서버가 잘못했습니다.");
        }
    }
}
