package com.words_hanjoom.domain.feedback.repository;

import com.words_hanjoom.domain.feedback.entity.AnswerComparisons;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<AnswerComparisons, Long> {
}
