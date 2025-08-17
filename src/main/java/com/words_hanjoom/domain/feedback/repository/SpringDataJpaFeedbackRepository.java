package com.words_hanjoom.domain.feedback.repository;

import com.words_hanjoom.domain.feedback.entity.ScrapActivities;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<ScrapActivities, Long> {
}
