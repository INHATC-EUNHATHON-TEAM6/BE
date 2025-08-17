package com.words_hanjoom.domain.feedback.repository;

import com.words_hanjoom.domain.feedback.entity.ScrapActivities;

public interface FeedbackRepository {
    ScrapActivities save(ScrapActivities activities);
}
