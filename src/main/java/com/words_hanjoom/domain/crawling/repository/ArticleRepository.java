package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    // 특정 URL을 가진 기사가 존재하는지 확인하는 메서드
    boolean existsByArticleUrl(String articleUrl);
}