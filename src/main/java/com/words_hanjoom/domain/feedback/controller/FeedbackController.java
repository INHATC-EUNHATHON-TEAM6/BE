package com.words_hanjoom.domain.feedback.controller;

import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackDto;
import com.words_hanjoom.domain.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService feedbackService;

    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestBody ScrapActivityDto activity) {
        try {
            String title = activity.getTitle();
            String summary = activity.getSummary();
            String category = activity.getCategory();
            List<String> keywords = activity.getKeywords();
            List<String> vocabularies = activity.getVocabularies();
            String comment = activity.getComment();
            FeedbackDto result = feedbackService.feedbackScrapActivity(title, summary, category, keywords, vocabularies, comment);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("서버 측 문제입니다.");
        }
    }
}
