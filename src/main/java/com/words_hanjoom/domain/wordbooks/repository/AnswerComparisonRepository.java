package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.ScrapActivity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerComparisonRepository extends JpaRepository<ScrapActivity, Long> {
}