package com.words_hanjoom.domain.feedback.repository;

import com.words_hanjoom.domain.feedback.entity.ScrapActivities;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataJpaFeedbackRepository extends JpaRepository<ScrapActivities, Long>, FeedbackRepository {
}
