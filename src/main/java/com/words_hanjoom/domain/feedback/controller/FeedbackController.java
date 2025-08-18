package com.words_hanjoom.domain.feedback.controller;

import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackResponseDto;
import com.words_hanjoom.domain.feedback.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FeedbackController {
    private final FeedbackService feedbackService;

    @Autowired
    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestBody ScrapActivityDto activity) {
        try {
            FeedbackResponseDto result = feedbackService.feedbackScrapActivity(activity);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("서버가 잘못했습니다.");
        }
    }
}
