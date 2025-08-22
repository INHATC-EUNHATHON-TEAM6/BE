package com.words_hanjoom.domain.feedback.repository;

import com.words_hanjoom.domain.feedback.dto.response.FeedbackThisMonthActivityDto;
import com.words_hanjoom.domain.feedback.entity.ScrapActivities;

import java.time.LocalDateTime;
import java.util.List;

public interface FeedbackRepository {
    ScrapActivities save(ScrapActivities activities);
    List<ScrapActivities> findByUserIdAndArticleId(long userId, long articleId);
    List<FeedbackThisMonthActivityDto> findByUserIdAndYearAndMonthAndDay(long userId, LocalDateTime startDate, LocalDateTime endDate);
}
