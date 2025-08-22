package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    // 특정 Url을 가진 Article이 존재하는지 확인하는 메소드
    boolean existsByArticleUrl(String url);

    Optional<Article> findByArticleUrl(String url);
}