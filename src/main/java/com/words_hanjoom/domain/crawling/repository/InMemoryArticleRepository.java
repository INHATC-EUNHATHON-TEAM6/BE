package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.entity.Article;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class InMemoryArticleRepository implements ArticleRepository {
    private final List<Article> store = new ArrayList<>();
    private long seq = 1L; // article_id 시퀀스

    @Override
    public void save(Article article) {
        if (article.getArticleId() == null) {
            article.setArticleId(seq++);
        }
        if (article.getCreatedAt() == null) {
            article.setCreatedAt(Instant.now());
        }
        store.add(article);
    }

    public List<Article> findAll() { return List.copyOf(store); }

    /** 미리보기 출력 */
    public void printSample(int limit) {
        System.out.println("=== 저장 미리보기 ===");
                store.stream().limit(limit).forEach(a -> {
                    System.out.printf("id=%d | catId=%d | 제목=%s | 날짜=%s | 신문사=%s | 기자=%s | 링크=%s ",
                            a.getArticleId(), a.getCategoryId(), a.getTitle(), a.getPublishedAt(), a.getPublisher(), a.getReporterName(), a.getArticleUrl());
                });
        System.out.println("===================");
    }
}
