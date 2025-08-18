package com.words_hanjoom.domain.crawling.entity;


import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "articles")
@Getter
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // InMemory DB에서 자동 증가
    @Column(name = "article_id")
    private Long articleId;       // PK (임시: InMemory에서 자동 증가)

    @Column(name = "category_id")
    private Long categoryId;   // FK -> 카테고리 테이블(ID 참조)

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;       // 본문

    @Column(name = "published_at")
    private String publishedAt;   // 작성일자

    @Column(name = "reporter_name")
    private String reporterName;  // 기자명

    @Column(name = "publisher")
    private String publisher;     // 신문사

    @Column(name = "article_url")
    private String articleUrl;    // 기사링크

    @Column(name = "created_at")
    private LocalDateTime createdAt;    // 생성시각

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;    // 삭제시각(소프트 삭제용) - null이면 유효

    public Article() {
    }

    @Builder
    public Article(Long articleId, Long categoryId, String title, String content,
                   String publishedAt, String reporterName, String publisher,
                   String articleUrl, LocalDateTime createdAt, LocalDateTime deletedAt) {
        this.articleId = articleId;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
        this.publishedAt = publishedAt;
        this.reporterName = reporterName;
        this.publisher = publisher;
        this.articleUrl = articleUrl;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }
}
