package com.words_hanjoom.domain.feedback.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.words_hanjoom.domain.feedback.entity.Article;

public interface SpringDataJpaArticleRepository extends JpaRepository<Article, Long> {
}
