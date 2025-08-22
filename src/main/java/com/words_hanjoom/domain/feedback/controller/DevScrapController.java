// src/main/java/com/words_hanjoom/domain/feedback/controller/DevScrapController.java
package com.words_hanjoom.domain.feedback.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbacksDto;
import com.words_hanjoom.domain.feedback.service.FeedbackService;
import com.words_hanjoom.domain.users.entity.User;
import com.words_hanjoom.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevScrapController {

    private final FeedbackService feedbackService;
    private final UserRepository userRepository;

    // 헤더로 user_id(pk) 받기
    @PostMapping("/scrap-activities")
    public FeedbacksDto save(@RequestHeader("X-User-Id") Long userId,
                             @RequestBody ScrapActivityDto body) throws JsonProcessingException {

        // pk → loginId 변환 (서비스 시그니처 유지)
        String loginId = userRepository.findById(userId)
                .map(User::getLoginId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));

        return feedbackService.feedbackScrapActivity(loginId, body);
    }
}
