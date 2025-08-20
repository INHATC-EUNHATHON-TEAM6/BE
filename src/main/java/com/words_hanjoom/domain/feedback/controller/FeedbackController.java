package com.words_hanjoom.domain.feedback.controller;

import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbacksDto;
import com.words_hanjoom.domain.feedback.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FeedbackController {
    private final FeedbackService feedbackService;

    @Autowired
    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/feedback/{articleId}")
    public ResponseEntity<?> getFeedback(@PathVariable("articleId") long articleId) {
        try {
            FeedbacksDto result = feedbackService.getScrapActivityRecord(articleId);
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
