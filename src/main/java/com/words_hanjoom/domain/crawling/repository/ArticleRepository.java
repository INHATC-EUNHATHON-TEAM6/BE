package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    // 특정 Url을 가진 Article이 존재하는지 확인하는 메소드
    boolean existsByArticleUrl(String url);

    Optional<Article> findByArticleUrl(String url);

    // null 방지: publishedAt이 null인 행 제외하고 싶다면 아래처럼
//    Optional<Article> findTopByCategoryIdAndDeletedAtIsNullAndPublishedAtIsNotNullOrderByPublishedAtDesc(Long categoryId);

    /**
     * 유저가 고른 카테고리들(user_categories) 중에서,
     * 해당 유저가 스크랩한 적 없는(scap_activities.user_id = :userId) 기사 하나(최신)만 선택.
     */
    @Query(value = """
    SELECT a.*
    FROM articles a
    WHERE a.category_id = :categoryId
      AND a.deleted_at IS NULL
      AND a.published_at IS NOT NULL
      AND NOT EXISTS (
            SELECT 1
            FROM scrap_activities sa
            WHERE sa.article_id = a.article_id
              AND sa.user_id    = :userId
      )
    ORDER BY a.published_at DESC,
             a.article_id DESC
    LIMIT 1
    """, nativeQuery = true)
    Optional<Article> pickOneArticleForUserInCategory(@Param("userId") Long userId,
                                                      @Param("categoryId") Long categoryId);
}